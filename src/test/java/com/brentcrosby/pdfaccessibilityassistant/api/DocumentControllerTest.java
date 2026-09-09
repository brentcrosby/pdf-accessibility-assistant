package com.brentcrosby.pdfaccessibilityassistant.api;

import com.brentcrosby.pdfaccessibilityassistant.domain.SourceType;
import com.brentcrosby.pdfaccessibilityassistant.domain.StoredDocument;
import com.brentcrosby.pdfaccessibilityassistant.service.DocumentWorkflowService;
import com.brentcrosby.pdfaccessibilityassistant.service.InMemoryDocumentStore;
import com.brentcrosby.pdfaccessibilityassistant.service.PdfAnalysisService;
import com.brentcrosby.pdfaccessibilityassistant.service.PdfExportService;
import com.brentcrosby.pdfaccessibilityassistant.service.PdfTextRegionService;
import com.brentcrosby.pdfaccessibilityassistant.support.PdfFixtureFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentControllerTest {
    @Test
    void servesPageObservationsAndRastersAndReportsMissingDocumentsAndInvalidPages() throws Exception {
        var analysis = new PdfAnalysisService();
        var workflow = new DocumentWorkflowService(new InMemoryDocumentStore(), analysis, new PdfExportService(analysis));
        var controller = new DocumentController(workflow, new PdfTextRegionService());
        var mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
        var source = workflow.upload("sample.pdf", SourceType.SYNTHETIC,
                com.brentcrosby.pdfaccessibilityassistant.support.WorkbenchFixture.create());
        String root = "/api/documents/" + source.id();
        mvc.perform(get(root + "/pages/1/observations")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.regions.length()").value(7))
                .andExpect(jsonPath("$.geometryVersion").value("page-crop-v2"));
        mvc.perform(get(root + "/pages/2/preview")).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG)).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get(root + "/pages/0/observations")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get(root + "/pages/5/preview")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/documents/" + UUID.randomUUID() + "/pages/1/observations")).andExpect(status().isNotFound());
    }

    @Test
    void servesTheOriginalBytesInlineWithoutCachingForTheReadOnlyPreview() throws Exception {
        PdfAnalysisService analysisService = new PdfAnalysisService();
        DocumentWorkflowService workflow = new DocumentWorkflowService(
                new InMemoryDocumentStore(), analysisService, new PdfExportService(analysisService));
        DocumentController controller = new DocumentController(workflow, new PdfTextRegionService());
        byte[] originalBytes = PdfFixtureFactory.pdf("Preview source", "en", false);
        StoredDocument document = workflow.upload("source.pdf", SourceType.SYNTHETIC, originalBytes);

        var response = controller.originalPreview(document.id());

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("inline");
        assertThat(response.getBody()).isEqualTo(originalBytes);
    }
}
