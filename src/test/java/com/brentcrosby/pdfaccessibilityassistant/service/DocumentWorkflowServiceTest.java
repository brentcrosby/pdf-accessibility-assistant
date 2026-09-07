package com.brentcrosby.pdfaccessibilityassistant.service;

import com.brentcrosby.pdfaccessibilityassistant.domain.ReviewDecision;
import com.brentcrosby.pdfaccessibilityassistant.domain.SourceType;
import com.brentcrosby.pdfaccessibilityassistant.domain.StoredDocument;
import com.brentcrosby.pdfaccessibilityassistant.support.PdfFixtureFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentWorkflowServiceTest {
    private final PdfAnalysisService analysisService = new PdfAnalysisService();
    private final DocumentWorkflowService workflow = new DocumentWorkflowService(
            new InMemoryDocumentStore(), analysisService, new PdfExportService(analysisService));

    @Test
    void recordsHumanConfirmedLanguageThenUsesItDuringExport() throws Exception {
        byte[] fixture = PdfFixtureFactory.pdf("Example title", null, false);
        StoredDocument document = workflow.upload("example.pdf", SourceType.SYNTHETIC, fixture);

        workflow.recordReview(document.id(), "MISSING_LANGUAGE", ReviewDecision.APPROVE, "en");

        assertThat(workflow.document(document.id()).reviews()).containsKey("MISSING_LANGUAGE");
        assertThat(workflow.export(document.id()).reanalysis().language()).isEqualTo("en");
    }
}
