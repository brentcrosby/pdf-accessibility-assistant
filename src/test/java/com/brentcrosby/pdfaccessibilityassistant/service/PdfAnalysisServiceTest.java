package com.brentcrosby.pdfaccessibilityassistant.service;

import com.brentcrosby.pdfaccessibilityassistant.domain.IssueDisposition;
import com.brentcrosby.pdfaccessibilityassistant.domain.PdfAnalysis;
import com.brentcrosby.pdfaccessibilityassistant.support.PdfFixtureFactory;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void rejectsPasswordProtectedPdfWithoutInspectingItsContents() throws Exception {
        byte[] encrypted = encryptedFixture();

        assertThatThrownBy(() -> service.analyze(encrypted))
                .isInstanceOf(PdfInputException.class)
                .hasMessage("Password-protected PDFs are not supported in this V1 slice.");
    }

    @Test
    void rejectsMalformedPdfWithBoundedError() {
        byte[] malformed = "%PDF-1.7\nnot a complete PDF".getBytes();

        assertThatThrownBy(() -> service.analyze(malformed))
                .isInstanceOf(PdfInputException.class)
                .hasMessage("The file could not be opened as a supported, unencrypted PDF.");
    }

    private byte[] encryptedFixture() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.protect(new StandardProtectionPolicy("owner", "user", new AccessPermission()));
            document.save(output);
            return output.toByteArray();
        }
    }
}
