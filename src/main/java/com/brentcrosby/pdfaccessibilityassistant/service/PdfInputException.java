package com.brentcrosby.pdfaccessibilityassistant.service;

public class PdfInputException extends RuntimeException {
    public PdfInputException(String message) {
        super(message);
    }

    public PdfInputException(String message, Throwable cause) {
        super(message, cause);
    }
}
