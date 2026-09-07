package com.brentcrosby.pdfaccessibilityassistant.service;

import com.brentcrosby.pdfaccessibilityassistant.domain.IssueDisposition;
import com.brentcrosby.pdfaccessibilityassistant.domain.PdfAnalysis;
import com.brentcrosby.pdfaccessibilityassistant.support.PdfFixtureFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PdfAnalysisServiceTest {
    private final PdfAnalysisService service = new PdfAnalysisService();

    @Test
    void reportsOnlyBoundedMetadataAndTaggingObservations() throws Exception {
        byte[] fixture = PdfFixtureFactory.pdf("Example title", null, false);

        PdfAnalysis analysis = service.analyze(fixture);

        assertThat(analysis.pageCount()).isEqualTo(1);
        assertThat(analysis.title()).isEqualTo("Example title");
        assertThat(analysis.language()).isNull();
        assertThat(analysis.displayDocumentTitle()).isFalse();
        assertThat(analysis.issues()).extracting(issue -> issue.code())
                .containsExactlyInAnyOrder("DISPLAY_TITLE_DISABLED", "MISSING_LANGUAGE", "UNTAGGED_DOCUMENT");
        assertThat(analysis.issues()).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo("DISPLAY_TITLE_DISABLED");
            assertThat(issue.disposition()).isEqualTo(IssueDisposition.AUTOMATIC_CANDIDATE);
        });
    }
}
