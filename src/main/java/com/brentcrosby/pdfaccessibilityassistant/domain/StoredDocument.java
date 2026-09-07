package com.brentcrosby.pdfaccessibilityassistant.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StoredDocument {
    private final UUID id;
    private final String originalFilename;
    private final SourceType sourceType;
    private final byte[] originalBytes;
    private final String sha256;
    private final Instant uploadedAt;
    private final Map<String, RecordedReview> reviews = new ConcurrentHashMap<>();

    public StoredDocument(UUID id, String originalFilename, SourceType sourceType, byte[] originalBytes, String sha256, Instant uploadedAt) {
        this.id = id;
        this.originalFilename = originalFilename;
        this.sourceType = sourceType;
        this.originalBytes = originalBytes.clone();
        this.sha256 = sha256;
        this.uploadedAt = uploadedAt;
    }

    public UUID id() { return id; }
    public String originalFilename() { return originalFilename; }
    public SourceType sourceType() { return sourceType; }
    public byte[] originalBytes() { return originalBytes.clone(); }
    public String sha256() { return sha256; }
    public Instant uploadedAt() { return uploadedAt; }
    public Map<String, RecordedReview> reviews() { return Map.copyOf(reviews); }

    public void recordReview(String issueCode, RecordedReview review) {
        reviews.put(issueCode, review);
    }
}
