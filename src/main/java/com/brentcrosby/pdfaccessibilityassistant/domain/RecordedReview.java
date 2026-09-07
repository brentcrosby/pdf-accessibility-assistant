package com.brentcrosby.pdfaccessibilityassistant.domain;

import java.time.Instant;

public record RecordedReview(ReviewDecision decision, String value, Instant recordedAt) {
}
