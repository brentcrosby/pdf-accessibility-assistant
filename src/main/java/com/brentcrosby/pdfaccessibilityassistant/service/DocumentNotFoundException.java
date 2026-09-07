package com.brentcrosby.pdfaccessibilityassistant.service;

public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException() {
        super("Document was not found in this in-memory session.");
    }
}
