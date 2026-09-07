package com.brentcrosby.pdfaccessibilityassistant.api;

import com.brentcrosby.pdfaccessibilityassistant.domain.AccessibilityIssue;
import com.brentcrosby.pdfaccessibilityassistant.domain.PdfAnalysis;
import com.brentcrosby.pdfaccessibilityassistant.domain.RecordedReview;
import com.brentcrosby.pdfaccessibilityassistant.domain.SourceType;
import com.brentcrosby.pdfaccessibilityassistant.domain.StoredDocument;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String originalFilename,
        SourceType sourceType,
        String originalSha256,
        Instant uploadedAt,
        PdfSnapshot analysis,
        Map<String, RecordedReview> recordedReviews,
        List<String> limitations
) {
    public static DocumentResponse from(StoredDocument document, PdfAnalysis analysis) {
        return new DocumentResponse(
                document.id(),
                document.originalFilename(),
                document.sourceType(),
                document.sha256(),
                document.uploadedAt(),
                PdfSnapshot.from(analysis),
                document.reviews(),
                List.of(
                        "Results are bounded technical observations, not a PAC, PDF/UA, WCAG, Section 508, or legal-compliance guarantee.",
                        "Tagging observations do not establish semantic reading order or accessibility.",
                        "Title writing is withheld in this first slice until the title/XMP synchronization spike is complete."
                ));
    }

    public record PdfSnapshot(
            int pageCount,
            boolean encrypted,
            String title,
            String language,
            boolean displayDocumentTitle,
            boolean xmpPresent,
            boolean markedAsTagged,
            boolean structureTreePresent,
            List<AccessibilityIssue> issues
    ) {
        static PdfSnapshot from(PdfAnalysis analysis) {
            return new PdfSnapshot(
                    analysis.pageCount(),
                    analysis.encrypted(),
                    analysis.title(),
                    analysis.language(),
                    analysis.displayDocumentTitle(),
                    analysis.xmpPresent(),
                    analysis.markedAsTagged(),
                    analysis.structureTreePresent(),
                    analysis.issues());
        }
    }
}
