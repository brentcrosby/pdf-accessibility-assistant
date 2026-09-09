package com.brentcrosby.pdfaccessibilityassistant.service;

import com.brentcrosby.pdfaccessibilityassistant.support.WorkbenchFixture;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import javax.imageio.ImageIO;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import static org.assertj.core.api.Assertions.*;

class PdfObservationServiceTest {
    private final PdfObservationService service = new PdfObservationService();

    @Test void identifiesContentKindsAndTextAboveTheBaselineWithoutMutatingOriginal() throws Exception {
        byte[] original = WorkbenchFixture.create();
        byte[] before = original.clone();
        var page = service.inspect(original, 1);
        assertThat(page.regions()).extracting(r -> r.kind()).contains("TEXT", "PATH", "IMAGE");
        var title = page.regions().stream().filter(r -> r.text().equals("Review the page content")).findFirst().orElseThrow();
        assertThat(title.bounds().y()).isLessThan((720d - 600) / 720);
        assertThat(title.bounds().y() + title.bounds().height()).isLessThan((720d - 590) / 720);
        assertThat(page.regions()).allSatisfy(r -> {
            assertThat(r.bounds().x()).isBetween(0d, 1d); assertThat(r.bounds().y()).isBetween(0d, 1d);
            assertThat(r.bounds().x() + r.bounds().width()).isLessThanOrEqualTo(1.000001);
            assertThat(r.bounds().y() + r.bounds().height()).isLessThanOrEqualTo(1.000001);
            assertThat(r.taggingStatus()).isEqualTo("NOT_EVALUATED");
        });
        assertThat(original).isEqualTo(before);
        assertThat(service.inspect(original, 1).regions()).isEqualTo(page.regions());
    }

    @ParameterizedTest @CsvSource({"0,.125,.72,.2,.08", "90,.2,.125,.08,.2", "180,.675,.2,.2,.08", "270,.72,.675,.08,.2"})
    void mapsKnownCroppedRectangleToItsActualRenderedPixels(int rotation, double x, double y, double w, double h) throws Exception {
        byte[] pdf;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(new PDRectangle(600, 800)); page.setCropBox(new PDRectangle(50, 100, 400, 500)); page.setRotation(rotation);
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) { stream.addRect(100, 200, 80, 40); stream.fill(); }
            document.save(out); pdf = out.toByteArray();
        }
        var snapshot = service.inspect(pdf, 1);
        var box = snapshot.regions().getFirst().bounds();
        assertThat(box.x()).isCloseTo(x, within(.00001)); assertThat(box.y()).isCloseTo(y, within(.00001));
        assertThat(box.width()).isCloseTo(w, within(.00001)); assertThat(box.height()).isCloseTo(h, within(.00001));
        var image = ImageIO.read(new ByteArrayInputStream(service.render(pdf, 1)));
        int minX = image.getWidth(), minY = image.getHeight(), maxX = 0, maxY = 0;
        for (int row = 0; row < image.getHeight(); row++) for (int col = 0; col < image.getWidth(); col++) {
            if ((image.getRGB(col, row) & 0xffffff) < 0x808080) {
                minX = Math.min(minX, col); minY = Math.min(minY, row); maxX = Math.max(maxX, col); maxY = Math.max(maxY, row);
            }
        }
        assertThat(minX / (double) image.getWidth()).isCloseTo(box.x(), within(.003));
        assertThat(minY / (double) image.getHeight()).isCloseTo(box.y(), within(.003));
        assertThat((maxX - minX + 1d) / image.getWidth()).isCloseTo(box.width(), within(.003));
        assertThat((maxY - minY + 1d) / image.getHeight()).isCloseTo(box.height(), within(.003));
    }

    @Test void handlesBlankAndImageOnlyPagesAndRejectsInvalidPageNumbers() throws Exception {
        byte[] pdf = WorkbenchFixture.create();
        assertThat(service.inspect(pdf, 3).regions()).isEmpty();
        assertThat(service.inspect(pdf, 4).regions()).singleElement().satisfies(r -> assertThat(r.kind()).isEqualTo("IMAGE"));
        assertThatThrownBy(() -> service.inspect(pdf, 0)).isInstanceOf(PdfInputException.class);
        assertThatThrownBy(() -> service.render(pdf, 5)).isInstanceOf(PdfInputException.class);
        var rotated = service.inspect(pdf, 2);
        assertThat(rotated.width()).isEqualTo(560); assertThat(rotated.height()).isEqualTo(480);
        var image = ImageIO.read(new ByteArrayInputStream(service.render(pdf, 2)));
        assertThat(image.getWidth()).isEqualTo(1120); assertThat(image.getHeight()).isEqualTo(960);
    }

    @Test void clipsOffPageBoundsAndCapsRegionCountWithAnExplicitPartialResult() throws Exception {
        assertThat(PdfObservationService.normalized(new Rectangle2D.Double(-20, 50, 40, 40), new PDRectangle(100,100), 0))
                .isEqualTo(new PdfObservationService.Bounds(0,.1,.2,.4));
        byte[] pdf;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(); document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                for (int i = 0; i <= PdfObservationService.MAX_REGIONS; i++) { stream.addRect(20,20,10,10); stream.fill(); }
            }
            document.save(out); pdf = out.toByteArray();
        }
        var page = service.inspect(pdf, 1);
        assertThat(page.truncated()).isTrue(); assertThat(page.regions()).hasSize(PdfObservationService.MAX_REGIONS);
        assertThatThrownBy(() -> service.render(pdf,1)).isInstanceOf(PdfInputException.class).hasMessageContaining("too complex");
    }

    @Test void rejectsOversizedPageDimensionsBeforeRendering() throws Exception {
        byte[] pdf;
        try (var doc = Loader.loadPDF(WorkbenchFixture.create()); var out = new ByteArrayOutputStream()) {
            doc.getPage(0).setMediaBox(new PDRectangle(30000, 30000)); doc.save(out); pdf = out.toByteArray();
        }
        assertThatThrownBy(() -> service.render(pdf, 1)).isInstanceOf(PdfInputException.class).hasMessageContaining("dimensions");
    }

    @Test void handlesTransformedFormContentWithoutLosingAnOuterPath() throws Exception {
        byte[] pdf;
        try (var doc = new PDDocument(); var out = new ByteArrayOutputStream()) {
            var page = new PDPage(new PDRectangle(400,400)); doc.addPage(page);
            var form = new org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject(doc);
            form.setBBox(new PDRectangle(100,100)); form.setResources(new PDResources());
            try (var stream = new PDFormContentStream(form)) { stream.addRect(10,20,30,40); stream.fill(); }
            try (var stream = new PDPageContentStream(doc,page)) {
                stream.moveTo(20,350); stream.lineTo(180,350);
                stream.saveGraphicsState(); stream.transform(org.apache.pdfbox.util.Matrix.getTranslateInstance(200,100));
                stream.drawForm(form); stream.restoreGraphicsState(); stream.stroke();
            }
            doc.save(out); pdf = out.toByteArray();
        }
        var regions = service.inspect(pdf,1).regions();
        assertThat(regions).hasSize(2);
        assertThat(regions.get(0).bounds().x()).isCloseTo(.525,within(.00001));
        assertThat(regions.get(0).bounds().y()).isCloseTo(.6,within(.00001));
        assertThat(regions.get(1).bounds().x()).isCloseTo(.05,within(.003));
        assertThat(regions.get(1).bounds().width()).isCloseTo(.4,within(.005));
    }

    @Test void scalesLargeValidPagesDownToThePreviewPixelLimit() throws Exception {
        byte[] pdf;
        try (var doc = new PDDocument(); var out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage(new PDRectangle(8000,4000))); doc.save(out); pdf = out.toByteArray();
        }
        var image = ImageIO.read(new ByteArrayInputStream(service.render(pdf,1)));
        assertThat(image.getWidth()).isLessThanOrEqualTo(1800);
        assertThat(image.getHeight()).isLessThanOrEqualTo(900);
    }
}
