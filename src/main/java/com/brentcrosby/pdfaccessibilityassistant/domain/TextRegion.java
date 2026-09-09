package com.brentcrosby.pdfaccessibilityassistant.domain;

public record TextRegion(
        String id,
        int pageNumber,
        double x,
        double y,
        double width,
        double height,
        String text
) {
}
