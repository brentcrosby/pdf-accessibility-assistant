package com.brentcrosby.pdfaccessibilityassistant.api;

import java.time.Instant;

public record ApiError(Instant timestamp, int status, String message) {
}
