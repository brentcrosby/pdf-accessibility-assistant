package com.brentcrosby.pdfaccessibilityassistant.service;

import com.brentcrosby.pdfaccessibilityassistant.domain.AccessibilityIssue;
import com.brentcrosby.pdfaccessibilityassistant.domain.IssueDisposition;
import com.brentcrosby.pdfaccessibilityassistant.domain.PdfAnalysis;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class PdfAnalysisService {
    private static final COSName VIEWER_PREFERENCES = COSName.getPDFName("ViewerPreferences");
    private static final COSName DISPLAY_DOC_TITLE = COSName.getPDFName("DisplayDocTitle");
    private static final COSName MARK_INFO = COSName.getPDFName("MarkInfo");
    private static final COSName MARKED = COSName.getPDFName("Marked");

    public PdfAnalysis analyze(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDDocumentCatalog catalog = document.getDocumentCatalog();
            PDDocumentInformation information = document.getDocumentInformation();
            COSDictionary catalogDictionary = catalog.getCOSObject();
            COSDictionary viewerPreferences = catalogDictionary.getCOSDictionary(VIEWER_PREFERENCES);
            COSDictionary markInfo = catalogDictionary.getCOSDictionary(MARK_INFO);

            String title = trimToNull(information.getTitle());
            String language = trimToNull(catalog.getLanguage());
            boolean displayDocumentTitle = viewerPreferences != null && viewerPreferences.getBoolean(DISPLAY_DOC_TITLE, false);
            boolean markedAsTagged = markInfo != null && markInfo.getBoolean(MARKED, false);
            boolean structureTreePresent = catalog.getStructureTreeRoot() != null;
            boolean xmpPresent = catalog.getMetadata() != null;

            List<AccessibilityIssue> issues = new ArrayList<>();
            if (title == null) {
                issues.add(new AccessibilityIssue(
                        "MISSING_TITLE",
                        IssueDisposition.REVIEW_REQUIRED,
                        "The PDF has no information-dictionary title.",
                        "Provide a title for review. Title writing is deferred until the title/XMP synchronization spike is proven safe.",
                        true));
            }
            if (title != null && !displayDocumentTitle) {
                issues.add(new AccessibilityIssue(
                        "DISPLAY_TITLE_DISABLED",
                        IssueDisposition.AUTOMATIC_CANDIDATE,
                        "The PDF has a title, but viewers are not configured to display it.",
                        "Enable the display-document-title preference during export.",
                        false));
            }
            if (language == null) {
                issues.add(new AccessibilityIssue(
                        "MISSING_LANGUAGE",
                        IssueDisposition.REVIEW_REQUIRED,
                        "The PDF has no document language in its catalog.",
                        "Ask a human reviewer to confirm a BCP 47 language tag before export.",
                        true));
            }
            if (!markedAsTagged && !structureTreePresent) {
                issues.add(new AccessibilityIssue(
                        "UNTAGGED_DOCUMENT",
                        IssueDisposition.REPORT_ONLY,
                        "The PDF is not marked as tagged and has no structure tree.",
                        "Structural remediation is outside this V1 slice; use expert/manual follow-up.",
                        false));
            } else if (markedAsTagged != structureTreePresent) {
                issues.add(new AccessibilityIssue(
                        "TAGGING_STATE_INCONCLUSIVE",
                        IssueDisposition.REPORT_ONLY,
                        "The tagged flag and structure-tree signals do not agree.",
                        "Treat this as a report-only observation and inspect with appropriate tools.",
                        false));
            }

            return new PdfAnalysis(
                    document.getNumberOfPages(),
                    document.isEncrypted(),
                    title,
                    language,
                    displayDocumentTitle,
                    xmpPresent,
                    markedAsTagged,
                    structureTreePresent,
                    List.copyOf(issues));
        } catch (InvalidPasswordException exception) {
            throw new PdfInputException("Password-protected PDFs are not supported in this V1 slice.", exception);
        } catch (IOException exception) {
            throw new PdfInputException("The file could not be opened as a supported, unencrypted PDF.", exception);
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
