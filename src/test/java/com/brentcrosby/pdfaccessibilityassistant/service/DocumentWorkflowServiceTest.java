package com.brentcrosby.pdfaccessibilityassistant.service;

import com.brentcrosby.pdfaccessibilityassistant.domain.ReviewDecision;
import com.brentcrosby.pdfaccessibilityassistant.domain.SourceType;
import com.brentcrosby.pdfaccessibilityassistant.domain.StoredDocument;
import com.brentcrosby.pdfaccessibilityassistant.support.PdfFixtureFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void recordsMissingTitleReviewButDoesNotWriteTitleInThisSlice() throws Exception {
        byte[] fixture = PdfFixtureFactory.pdf(null, "en", false);
        StoredDocument document = workflow.upload("missing-title.pdf", SourceType.SYNTHETIC, fixture);

        workflow.recordReview(document.id(), "MISSING_TITLE", ReviewDecision.APPROVE, "Reviewed title");

        assertThat(workflow.document(document.id()).reviews()).containsKey("MISSING_TITLE");
        assertThat(workflow.export(document.id()).appliedActions())
                .doesNotContain("REVIEWED_TITLE_APPLIED");
        assertThat(workflow.export(document.id()).reanalysis().title()).isNull();
    }

    @Test
    void retainsOriginalBytesAfterExport() throws Exception {
        byte[] fixture = PdfFixtureFactory.pdf("Example title", "en", false);
        StoredDocument document = workflow.upload("original.pdf", SourceType.SYNTHETIC, fixture);

        workflow.export(document.id());

        assertThat(workflow.document(document.id()).originalBytes()).isEqualTo(fixture);
    }

    @Test
    void rejectsInvalidOrMultilingualDocumentLanguageValues() throws Exception {
        byte[] fixture = PdfFixtureFactory.pdf("Example title", null, false);
        StoredDocument document = workflow.upload("language.pdf", SourceType.SYNTHETIC, fixture);

        assertThatThrownBy(() -> workflow.recordReview(document.id(), "MISSING_LANGUAGE", ReviewDecision.APPROVE, "en_US"))
                .isInstanceOf(PdfInputException.class)
                .hasMessageContaining("valid BCP 47 language tag");
        assertThatThrownBy(() -> workflow.recordReview(document.id(), "MISSING_LANGUAGE", ReviewDecision.APPROVE, "en-US,fr-FR"))
                .isInstanceOf(PdfInputException.class)
                .hasMessageContaining("valid BCP 47 language tag");
    }
}
