package com.brentcrosby.pdfaccessibilityassistant.service;

import com.brentcrosby.pdfaccessibilityassistant.domain.PdfAnalysis;
import com.brentcrosby.pdfaccessibilityassistant.domain.RecordedReview;
import com.brentcrosby.pdfaccessibilityassistant.domain.ReviewDecision;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PdfExportService {
    private static final COSName VIEWER_PREFERENCES = COSName.getPDFName("ViewerPreferences");
    private static final COSName DISPLAY_DOC_TITLE = COSName.getPDFName("DisplayDocTitle");
    private final PdfAnalysisService analysisService;

    public PdfExportService(PdfAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    public ExportedPdf export(byte[] originalBytes, Map<String, RecordedReview> reviews) {
        PdfAnalysis before = analysisService.analyze(originalBytes);
        List<String> appliedActions = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(originalBytes);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (before.title() != null && !before.displayDocumentTitle()) {
                COSDictionary catalog = document.getDocumentCatalog().getCOSObject();
                COSDictionary viewerPreferences = catalog.getCOSDictionary(VIEWER_PREFERENCES);
                if (viewerPreferences == null) {
                    viewerPreferences = new COSDictionary();
                    catalog.setItem(VIEWER_PREFERENCES, viewerPreferences);
                }
                viewerPreferences.setBoolean(DISPLAY_DOC_TITLE, true);
                appliedActions.add("DISPLAY_DOCUMENT_TITLE_ENABLED");
            }

            RecordedReview languageReview = reviews.get("MISSING_LANGUAGE");
            if (languageReview != null && languageReview.decision() == ReviewDecision.APPROVE) {
                document.getDocumentCatalog().setLanguage(languageReview.value());
                appliedActions.add("REVIEWED_LANGUAGE_APPLIED");
            }

            document.save(output);
            byte[] exportedBytes = output.toByteArray();
            PdfAnalysis after = analysisService.analyze(exportedBytes);
            verify(before, after, appliedActions);
            return new ExportedPdf(exportedBytes, List.copyOf(appliedActions), after);
        } catch (IOException exception) {
            throw new PdfInputException("The remediated copy could not be saved safely.", exception);
        }
    }

    private void verify(PdfAnalysis before, PdfAnalysis after, List<String> appliedActions) {
        if (before.pageCount() != after.pageCount()) {
            throw new PdfInputException("Export verification failed because the page count changed.");
        }
        if (before.encrypted() != after.encrypted()) {
            throw new PdfInputException("Export verification failed because encryption state changed.");
        }
        if (appliedActions.contains("DISPLAY_DOCUMENT_TITLE_ENABLED") && !after.displayDocumentTitle()) {
            throw new PdfInputException("Export verification failed because display-document-title was not enabled.");
        }
        if (appliedActions.contains("REVIEWED_LANGUAGE_APPLIED") && after.language() == null) {
            throw new PdfInputException("Export verification failed because document language is still missing.");
        }
    }

    public record ExportedPdf(byte[] bytes, List<String> appliedActions, PdfAnalysis reanalysis) {
    }
}
