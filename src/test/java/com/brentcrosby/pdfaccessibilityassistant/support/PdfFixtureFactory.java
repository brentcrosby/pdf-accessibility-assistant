package com.brentcrosby.pdfaccessibilityassistant.support;

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class PdfFixtureFactory {
    private PdfFixtureFactory() {
    }

    public static byte[] pdf(String title, String language, boolean displayDocumentTitle) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            if (title != null) {
                document.getDocumentInformation().setTitle(title);
            }
            if (language != null) {
                document.getDocumentCatalog().setLanguage(language);
            }
            COSDictionary viewerPreferences = new COSDictionary();
            viewerPreferences.setBoolean(COSName.getPDFName("DisplayDocTitle"), displayDocumentTitle);
            document.getDocumentCatalog().getCOSObject().setItem(COSName.getPDFName("ViewerPreferences"), viewerPreferences);
            document.save(output);
            return output.toByteArray();
        }
    }
}
