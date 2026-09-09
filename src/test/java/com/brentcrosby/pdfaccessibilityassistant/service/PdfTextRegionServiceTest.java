package com.brentcrosby.pdfaccessibilityassistant.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class PdfTextRegionServiceTest {
    private final PdfTextRegionService service = new PdfTextRegionService();

    @Test
    void reportsNormalizedTextRegions() throws Exception {
        byte[] fixture = textPdf("Visible review text");

        var regions = service.locate(fixture);

        assertThat(regions).singleElement().satisfies(region -> {
            assertThat(region.pageNumber()).isEqualTo(1);
            assertThat(region.text()).isEqualTo("Visible review text");
            assertThat(region.x()).isBetween(0d, 1d);
            assertThat(region.y()).isBetween(0d, 1d);
            assertThat(region.width()).isGreaterThan(0d);
            assertThat(region.height()).isGreaterThan(0d);
        });
    }

    private byte[] textPdf(String text) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 720);
                stream.showText(text);
                stream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
