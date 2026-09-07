package com.brentcrosby.pdfaccessibilityassistant.domain;

public record AccessibilityIssue(
        String code,
        IssueDisposition disposition,
        String message,
        String recommendedAction,
        boolean requiresHumanValue
) {
}
