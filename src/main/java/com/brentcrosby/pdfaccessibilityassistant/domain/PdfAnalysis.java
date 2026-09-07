package com.brentcrosby.pdfaccessibilityassistant.domain;

import java.util.List;

public record PdfAnalysis(
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
}
