package com.brentcrosby.pdfaccessibilityassistant.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDVectorFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDTransparencyGroup;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.util.Vector;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Content observations, deliberately independent of the PDF's semantic structure tree. */
@Service
public class PdfObservationService {
    public static final String GEOMETRY_VERSION = "page-crop-v2";
    public static final int MAX_REGIONS = 1500;
    private static final int MAX_OPERATIONS = 100_000;
    public record Bounds(double x, double y, double width, double height) {}
    public record Region(String id, int pageNumber, String kind, Bounds bounds, String text,
                         String geometryQuality, String taggingStatus, String altTextStatus,
                         int unmappedGlyphs, int sourceOperatorIndex, Integer mcid,
                         boolean artifact, boolean markedContentPresent, int markedSequence) {}
    public record PageObservations(int pageNumber, int pageCount, double width, double height,
                                   int rotation, String geometryVersion, List<Region> regions,
                                   boolean truncated, List<String> warnings) {}

    public PageObservations inspect(byte[] bytes, int pageNumber) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return inspect(document, pageNumber);
        } catch (IOException ex) {
            throw new PdfInputException("This page could not be inspected. Try another page or PDF.", ex);
        }
    }

    private PageObservations inspect(PDDocument document, int pageNumber) throws IOException {
        PDPage page = checkedPage(document, pageNumber);
        Collector collector = new Collector(page, pageNumber);
        boolean truncated = false;
        try {
            collector.processPage(page);
        } catch (ObservationLimit ex) {
            truncated = true;
            collector.warnings.add("Observation limit reached; this page's region list is partial.");
        }
        if (collector.markInvalid || !collector.marks.isEmpty()) collector.warnings.add("Malformed marked content: semantic membership is unresolved for this page.");
        boolean sideways = page.getRotation() == 90 || page.getRotation() == 270;
        PDRectangle box = page.getCropBox();
        return new PageObservations(pageNumber, document.getNumberOfPages(),
                sideways ? box.getHeight() : box.getWidth(), sideways ? box.getWidth() : box.getHeight(),
                page.getRotation(), GEOMETRY_VERSION, List.copyOf(collector.regions), truncated,
                List.copyOf(collector.warnings));
    }

    public byte[] render(byte[] bytes, int pageNumber) {
        try (PDDocument document = Loader.loadPDF(bytes); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var snapshot = inspect(document, pageNumber);
            if (snapshot.truncated()) throw new PdfInputException("This page is too complex for the bounded preview. Its partial region list is still available.");
            float scale = (float) Math.min(2, 1800 / Math.max(snapshot.width(), snapshot.height()));
            PDFRenderer renderer = new PDFRenderer(document);
            renderer.setSubsamplingAllowed(true);
            ImageIO.write(renderer.renderImage(pageNumber - 1, scale, ImageType.RGB), "png", output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new PdfInputException("The page preview could not be rendered.", ex);
        }
    }

    private PDPage checkedPage(PDDocument document, int number) {
        if (document.isEncrypted()) throw new PdfInputException("Encrypted PDFs are not supported by the review workbench.");
        if (number < 1 || number > document.getNumberOfPages()) throw new PdfInputException("The requested page does not exist.");
        PDPage page = document.getPage(number - 1);
        PDRectangle crop = page.getCropBox();
        if (!Float.isFinite(crop.getWidth()) || !Float.isFinite(crop.getHeight()) ||
                crop.getWidth() < 1 || crop.getHeight() < 1 || crop.getWidth() > 20000 || crop.getHeight() > 20000) {
            throw new PdfInputException("This page's dimensions exceed the preview limits.");
        }
        return page;
    }

    // PDF user-space bounds -> displayed crop box, with a top-left origin and clockwise page rotation.
    static Bounds normalized(Rectangle2D source, PDRectangle crop, int rotation) {
        if (!Double.isFinite(source.getX()) || !Double.isFinite(source.getY()) ||
                !Double.isFinite(source.getWidth()) || !Double.isFinite(source.getHeight())) return null;
        Rectangle2D r = source.createIntersection(new Rectangle2D.Double(crop.getLowerLeftX(), crop.getLowerLeftY(), crop.getWidth(), crop.getHeight()));
        if (r.isEmpty()) return null;
        double x = (r.getX() - crop.getLowerLeftX()) / crop.getWidth();
        double y = (crop.getUpperRightY() - r.getMaxY()) / crop.getHeight();
        double w = r.getWidth() / crop.getWidth(), h = r.getHeight() / crop.getHeight();
        return switch (rotation) {
            case 90 -> new Bounds(1 - y - h, x, h, w);
            case 180 -> new Bounds(1 - x - w, 1 - y - h, w, h);
            case 270 -> new Bounds(y, 1 - x - w, h, w);
            default -> new Bounds(x, y, w, h);
        };
    }

    private static final class ObservationLimit extends RuntimeException {}

    private static final class Collector extends PDFGraphicsStreamEngine {
        private final List<Region> regions = new ArrayList<>();
        private final Set<String> warnings = new LinkedHashSet<>();
        private final int pageNumber;
        private GeneralPath path = new GeneralPath();
        private int clipRule = -1;
        private int operations;
        private int operatorDepth;
        private int topOperatorIndex = -1;
        private record Mark(Integer mcid, boolean artifact, int sequence) {}
        private final List<Mark> marks = new ArrayList<>();
        private boolean markInvalid;
        private int nextSequence;
        private Rectangle2D textBounds;
        private final StringBuilder snippet = new StringBuilder();
        private int unmapped;
        private boolean approximateText;
        private final long started = System.nanoTime();

        Collector(PDPage page, int pageNumber) {
            super(page);
            this.pageNumber = pageNumber;
            warnings.add("Regions are content observations, not accessibility flags or semantic tags. Tagging, figure alt text and reading order have not been evaluated.");
            warnings.add("Bounds approximate painted content; overlays do not account for occlusion, optional layers, masks, annotations or pattern details.");
        }

        private void budget() {
            if (++operations > MAX_OPERATIONS || System.nanoTime() - started > 5_000_000_000L) throw new ObservationLimit();
        }

        @Override protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
            budget();
            if (getLevel() > 30) throw new ObservationLimit();
            if (operatorDepth == 0) topOperatorIndex++;
            String operation = operator.getName();
            if (operation.equals("BMC") || operation.equals("BDC")) {
                Integer mcid = null;
                if (operands.size() != (operation.equals("BMC") ? 1 : 2)) markInvalid = true;
                if (operands.isEmpty() || !(operands.getFirst() instanceof COSName)) markInvalid = true;
                boolean artifact = !operands.isEmpty() && COSName.ARTIFACT.equals(operands.getFirst());
                if (operation.equals("BDC")) {
                    COSDictionary props = operands.size() > 1 && operands.get(1) instanceof COSDictionary d ? d : null;
                    if (operands.size() > 1 && operands.get(1) instanceof COSName name && getResources() != null) {
                        var property = getResources().getProperties(name); props = property == null ? null : property.getCOSObject();
                    }
                    if (props == null) markInvalid = true;
                    else if (props.containsKey(COSName.MCID)) {
                        if (props.getDictionaryObject(COSName.MCID) instanceof COSInteger n && n.intValue() >= 0) mcid = n.intValue();
                        else markInvalid = true;
                    }
                }
                marks.add(new Mark(mcid,artifact,++nextSequence));
            } else if (operation.equals("EMC")) {
                if (!operands.isEmpty()) markInvalid = true;
                if (marks.isEmpty()) markInvalid = true; else marks.removeLast();
            }
            operatorDepth++;
            try { super.processOperator(operator, operands); }
            finally { operatorDepth--; }
        }

        @Override public void showForm(PDFormXObject form) throws IOException {
            var outerMarks = new ArrayList<>(marks);
            GeneralPath outerPath = path;
            int outerClip = clipRule;
            path = new GeneralPath(); clipRule = -1;
            try { super.showForm(form); }
            finally { if (!marks.equals(outerMarks)) markInvalid = true; marks.clear(); marks.addAll(outerMarks); path = outerPath; clipRule = outerClip; }
        }

        @Override public void showTransparencyGroup(PDTransparencyGroup form) throws IOException {
            var outerMarks = new ArrayList<>(marks);
            GeneralPath outerPath = path;
            int outerClip = clipRule;
            path = new GeneralPath(); clipRule = -1;
            try { super.showTransparencyGroup(form); }
            finally { if (!marks.equals(outerMarks)) markInvalid = true; marks.clear(); marks.addAll(outerMarks); path = outerPath; clipRule = outerClip; }
        }

        @Override protected void showText(byte[] string) throws IOException {
            textBounds = null;
            snippet.setLength(0);
            unmapped = 0;
            approximateText = false;
            super.showText(string);
            if (textBounds != null) add("TEXT", textBounds, snippet.toString(), approximateText ? "APPROXIMATE" : "GLYPH_BOUNDS", unmapped);
        }

        @Override protected void showGlyph(Matrix matrix, PDFont font, int code, Vector displacement) throws IOException {
            budget();
            var mode = getGraphicsState().getTextState().getRenderingMode();
            if (mode.isClip()) warnings.add("Text clipping is present; some overlay bounds may overestimate visible content.");
            if (!mode.isFill() && !mode.isStroke()) {
                warnings.add("Invisible or clipping-only text was skipped. Scanned-page OCR text is not reviewed by this view.");
                return;
            }
            String unicode = font.toUnicode(code);
            if (unicode == null) unmapped++;
            if (snippet.length() < 180) snippet.append(unicode == null ? "\uFFFD" : unicode);
            Shape glyph;
            AffineTransform transform = matrix.createAffineTransform();
            if (font instanceof PDVectorFont vectorFont) {
                glyph = vectorFont.getPath(code);
                transform.concatenate(font.getFontMatrix().createAffineTransform());
            } else {
                approximateText = true;
                glyph = new Rectangle2D.Double(0, -0.2, Math.max(Math.abs(displacement.getX()), .1), 1);
            }
            if (glyph == null || glyph.getBounds2D().isEmpty()) return;
            Rectangle2D bounds = transform.createTransformedShape(glyph).getBounds2D();
            textBounds = textBounds == null ? bounds : textBounds.createUnion(bounds);
        }

        private void add(String kind, Rectangle2D raw, String label, String quality, int missing) {
            Rectangle2D clipped = raw.createIntersection(getGraphicsState().getCurrentClippingPath().getBounds2D());
            Bounds box = normalized(clipped, getPage().getCropBox(), getPage().getRotation());
            if (box == null) return;
            if (regions.size() >= MAX_REGIONS) throw new ObservationLimit();
            String clean = label.replaceAll("\\p{Cntrl}", " ").strip();
            clean = clean.codePoints().limit(180).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
            Mark owner = marks.stream().filter(m -> m.mcid() != null).reduce((a,b)->b).orElse(null);
            regions.add(new Region("p" + pageNumber + "-o" + (regions.size() + 1), pageNumber, kind, box,
                    clean, quality, "NOT_EVALUATED", "NOT_EVALUATED", missing,
                    operatorDepth == 1 ? topOperatorIndex : -1, owner == null ? null : owner.mcid(),
                    marks.stream().anyMatch(Mark::artifact), !marks.isEmpty(), owner == null ? -1 : owner.sequence()));
        }

        @Override public void drawImage(PDImage image) {
            budget();
            if ((long) image.getWidth() * image.getHeight() > 25_000_000L) throw new PdfInputException("An image exceeds the 25-megapixel preview limit.");
            Shape unit = getGraphicsState().getCurrentTransformationMatrix().createAffineTransform()
                    .createTransformedShape(new Rectangle2D.Double(0, 0, 1, 1));
            add("IMAGE", unit.getBounds2D(), "Image (" + image.getWidth() + " × " + image.getHeight() + " pixels)", "TRANSFORMED_BOUNDS", 0);
        }

        @Override public void appendRectangle(Point2D a, Point2D b, Point2D c, Point2D d) {
            path.moveTo(a.getX(), a.getY()); path.lineTo(b.getX(), b.getY());
            path.lineTo(c.getX(), c.getY()); path.lineTo(d.getX(), d.getY()); path.closePath();
        }
        @Override public void moveTo(float x, float y) { path.moveTo(x, y); }
        @Override public void lineTo(float x, float y) { path.lineTo(x, y); }
        @Override public void curveTo(float a, float b, float c, float d, float e, float f) { path.curveTo(a, b, c, d, e, f); }
        @Override public Point2D getCurrentPoint() { return path.getCurrentPoint(); }
        @Override public void closePath() { path.closePath(); }
        @Override public void clip(int rule) { clipRule = rule; }
        @Override public void endPath() {
            if (clipRule != -1) {
                path.setWindingRule(clipRule);
                getGraphicsState().intersectClippingPath(path);
                clipRule = -1;
            }
            path = new GeneralPath();
        }
        private void paintedPath(boolean stroke, boolean fill) {
            Rectangle2D bounds = path.getBounds2D();
            if (stroke) {
                float width = Math.max(.5f, Math.abs(transformWidth(getGraphicsState().getLineWidth())));
                bounds = bounds.createUnion(new BasicStroke(width).createStrokedShape(path).getBounds2D());
            }
            add("PATH", bounds, fill && stroke ? "Filled and stroked path" : fill ? "Filled path" : "Stroked path", "APPROXIMATE", 0);
            endPath();
        }
        @Override public void strokePath() { paintedPath(true, false); }
        @Override public void fillPath(int rule) { path.setWindingRule(rule); paintedPath(false, true); }
        @Override public void fillAndStrokePath(int rule) { path.setWindingRule(rule); paintedPath(true, true); }
        @Override public void shadingFill(COSName name) { warnings.add("Shading content is visible in the preview but has no selectable region."); }
    }
}
