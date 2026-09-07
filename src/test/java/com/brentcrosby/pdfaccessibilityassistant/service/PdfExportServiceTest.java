package com.brentcrosby.pdfaccessibilityassistant.service;

import com.brentcrosby.pdfaccessibilityassistant.domain.RecordedReview;
import com.brentcrosby.pdfaccessibilityassistant.domain.ReviewDecision;
import com.brentcrosby.pdfaccessibilityassistant.support.PdfFixtureFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PdfExportServiceTest {
    private final PdfAnalysisService analysisService = new PdfAnalysisService();
    private final PdfExportService exportService = new PdfExportService(analysisService);

    @Test
    void enablesDisplayTitleAndAppliesApprovedLanguageWithoutChangingSourceBytes() throws Exception {
        byte[] original = PdfFixtureFactory.pdf("Example title", null, false);
        byte[] originalSnapshot = original.clone();

        PdfExportService.ExportedPdf exported = exportService.export(
                original,
                Map.of("MISSING_LANGUAGE", new RecordedReview(ReviewDecision.APPROVE, "en-US", Instant.now())));

        assertThat(original).isEqualTo(originalSnapshot);
        assertThat(exported.appliedActions()).containsExactlyInAnyOrder(
                "DISPLAY_DOCUMENT_TITLE_ENABLED",
                "REVIEWED_LANGUAGE_APPLIED");
        assertThat(exported.reanalysis().displayDocumentTitle()).isTrue();
        assertThat(exported.reanalysis().language()).isEqualTo("en-US");
        assertThat(exported.reanalysis().title()).isEqualTo("Example title");
        assertThat(exported.reanalysis().pageCount()).isEqualTo(1);
    }
}
