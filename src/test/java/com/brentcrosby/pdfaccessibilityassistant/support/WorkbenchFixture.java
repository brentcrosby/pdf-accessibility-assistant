package com.brentcrosby.pdfaccessibilityassistant.support;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Synthetic data only. No checked-in PDF fixture is needed. */
public final class WorkbenchFixture {
    public static byte[] create() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.getDocumentInformation().setTitle("Synthetic review workbench");
            document.getDocumentCatalog().setLanguage("en");
            for (int index = 0; index < 4; index++) {
                PDPage page = new PDPage(new PDRectangle(600, 720));
                if (index == 1) { page.setCropBox(new PDRectangle(40, 80, 480, 560)); page.setRotation(90); }
                document.addPage(page);
                if (index == 2) continue;
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    if (index != 3) {
                        text(stream, 60, 600, 23, "Review the page content");
                        text(stream, 60, 565, 12, "Synthetic sample: text, an image and decorative rules.");
                        text(stream, 60, 540, 12, "Choose content to keep, defer or review as an artifact.");
                        stream.setStrokingColor(new Color(26, 92, 103)); stream.setLineWidth(3);
                        stream.moveTo(60, 520); stream.lineTo(500, 520); stream.stroke();
                        stream.setNonStrokingColor(new Color(224, 238, 240));
                        stream.addRect(60, 140, 440, 50); stream.fill();
                        stream.setNonStrokingColor(Color.BLACK); text(stream, 74, 159, 11, "Repeated footer candidate - human review required");
                    }
                    BufferedImage image = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
                    var g = image.createGraphics();
                    g.setColor(new Color(27, 78, 104)); g.fillRect(0, 0, 120, 80);
                    g.setColor(new Color(135, 210, 192)); g.fillOval(14, 12, 58, 58);
                    g.setColor(Color.WHITE); g.fillRect(83, 18, 19, 45); g.dispose();
                    stream.drawImage(LosslessFactory.createFromImage(document, image), 60, 260, 240, 160);
                }
            }
            document.save(out); return out.toByteArray();
        }
    }
    private static void text(PDPageContentStream stream, float x, float y, int size, String text) throws Exception {
        stream.beginText(); stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), size);
        stream.newLineAtOffset(x, y); stream.showText(text); stream.endText();
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Supply an explicit temporary PDF path.");
        Files.write(Path.of(args[0]), create());
    }
}
