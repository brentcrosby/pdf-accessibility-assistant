package com.brentcrosby.pdfaccessibilityassistant.api;

import com.brentcrosby.pdfaccessibilityassistant.domain.SourceType;
import com.brentcrosby.pdfaccessibilityassistant.service.*;
import com.brentcrosby.pdfaccessibilityassistant.support.WorkbenchFixture;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Base64;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ArtifactRepairControllerTest {
    @Test void checksApprovalValidatesIdentityAndReturnsADownloadableVerifiedPdf() throws Exception {
        var analysis = new PdfAnalysisService(); var observations = new PdfObservationService();
        var workflow = new DocumentWorkflowService(new InMemoryDocumentStore(),analysis,new PdfExportService(analysis));
        var repairs = new PdfArtifactRepairService(observations);
        var mvc = MockMvcBuilders.standaloneSetup(new ArtifactRepairController(workflow,repairs)).setControllerAdvice(new ApiExceptionHandler()).build();
        var source = workflow.upload("synthetic.pdf",SourceType.SYNTHETIC,WorkbenchFixture.create());
        String root = "/api/documents/" + source.id() + "/artifact-repairs";
        var mapper = new ObjectMapper();
        var body = new java.util.HashMap<String,Object>(Map.of("pageNumber",1,"regionId","p1-o4","originalSha256",source.sha256(),
                "geometryVersion",PdfObservationService.GEOMETRY_VERSION,"decorativeConfirmed",false));
        mvc.perform(post(root+"/check").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.eligible").value(true));
        mvc.perform(post(root).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body))).andExpect(status().isUnprocessableEntity());
        body.put("decorativeConfirmed",true);
        var response = mvc.perform(post(root).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.evidence.appliedToPdf").value(true))
                .andExpect(jsonPath("$.evidence.verification.catalogAndInfoGraphPreserved").value(true)).andReturn().getResponse();
        byte[] pdf = Base64.getDecoder().decode(mapper.readTree(response.getContentAsByteArray()).get("pdf").asText());
        var continued = workflow.upload("repaired.pdf",SourceType.SYNTHETIC,pdf);
        body.put("originalSha256",continued.sha256());
        mvc.perform(post("/api/documents/"+continued.id()+"/artifact-repairs/check").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eligible").value(false));
        assertThat(workflow.document(source.id()).originalBytes()).isEqualTo(source.originalBytes());
        body.put("pageNumber",0);
        mvc.perform(post(root).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body))).andExpect(status().isBadRequest());
    }
}
