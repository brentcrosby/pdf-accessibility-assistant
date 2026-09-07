package com.brentcrosby.pdfaccessibilityassistant.service;

import com.brentcrosby.pdfaccessibilityassistant.domain.PdfAnalysis;
import com.brentcrosby.pdfaccessibilityassistant.domain.RecordedReview;
import com.brentcrosby.pdfaccessibilityassistant.domain.ReviewDecision;
import com.brentcrosby.pdfaccessibilityassistant.domain.SourceType;
import com.brentcrosby.pdfaccessibilityassistant.domain.StoredDocument;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class DocumentWorkflowService {
    private static final int MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;
    private final InMemoryDocumentStore store;
    private final PdfAnalysisService analysisService;
    private final PdfExportService exportService;

    public DocumentWorkflowService(InMemoryDocumentStore store, PdfAnalysisService analysisService, PdfExportService exportService) {
        this.store = store;
        this.analysisService = analysisService;
        this.exportService = exportService;
    }

    public StoredDocument upload(String filename, SourceType sourceType, byte[] bytes) {
        validateUpload(filename, bytes);
        analysisService.analyze(bytes);
        StoredDocument document = new StoredDocument(
                UUID.randomUUID(),
                safeFilename(filename),
                sourceType,
                bytes,
                sha256(bytes),
                Instant.now());
        return store.save(document);
    }

    public PdfAnalysis analysis(UUID id) {
        return analysisService.analyze(store.get(id).originalBytes());
    }

    public StoredDocument document(UUID id) {
        return store.get(id);
    }

    public StoredDocument recordReview(UUID id, String issueCode, ReviewDecision decision, String value) {
        StoredDocument document = store.get(id);
        PdfAnalysis analysis = analysisService.analyze(document.originalBytes());
        boolean knownReview = analysis.issues().stream()
                .anyMatch(issue -> issue.code().equals(issueCode) && issue.requiresHumanValue());
        if (!knownReview) {
            throw new PdfInputException("That issue cannot receive a human metadata review in this V1 slice.");
        }
        if (decision == ReviewDecision.APPROVE) {
            validateReviewedValue(issueCode, value);
        }
        document.recordReview(issueCode, new RecordedReview(decision, normalizeValue(value), Instant.now()));
        return document;
    }

    public PdfExportService.ExportedPdf export(UUID id) {
        StoredDocument document = store.get(id);
        return exportService.export(document.originalBytes(), document.reviews());
    }

    private void validateUpload(String filename, byte[] bytes) {
        if (filename == null || filename.isBlank() || !filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new PdfInputException("Upload a file with a .pdf filename.");
        }
        if (bytes == null || bytes.length == 0) {
            throw new PdfInputException("The uploaded PDF is empty.");
        }
        if (bytes.length > MAX_FILE_SIZE_BYTES) {
            throw new PdfInputException("The uploaded PDF exceeds the 10 MB V1 limit.");
        }
        if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
            throw new PdfInputException("The uploaded file does not have a PDF header.");
        }
    }

    private void validateReviewedValue(String issueCode, String value) {
        String normalized = normalizeValue(value);
        if (normalized == null) {
            throw new PdfInputException("An approved review must include a value.");
        }
        if ("MISSING_LANGUAGE".equals(issueCode)) {
            String languageTag = Locale.forLanguageTag(normalized).toLanguageTag();
            if ("und".equals(languageTag) || !languageTag.equalsIgnoreCase(normalized)) {
                throw new PdfInputException("Provide a valid BCP 47 language tag, for example en or en-US.");
            }
        }
        if ("MISSING_TITLE".equals(issueCode) && normalized.length() > 250) {
            throw new PdfInputException("Document title must be 250 characters or fewer.");
        }
    }

    private String normalizeValue(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String safeFilename(String filename) {
        return filename.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String sha256(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available in this Java runtime.", exception);
        }
    }
}
