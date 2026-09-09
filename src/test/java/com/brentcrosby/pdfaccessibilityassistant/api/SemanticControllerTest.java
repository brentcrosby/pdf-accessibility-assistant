package com.brentcrosby.pdfaccessibilityassistant.api;

import com.brentcrosby.pdfaccessibilityassistant.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SemanticControllerTest {
    @Test void demosSemanticMappingTransactionsAndBenchmarkValidateHttpInputs()throws Exception {
        var analysis=new PdfAnalysisService();var observations=new PdfObservationService();var semantics=new PdfSemanticService(observations);var proposals=new TagProposalService();
        var transactions=new PdfTransactionService(observations,semantics,proposals);var workflow=new DocumentWorkflowService(new InMemoryDocumentStore(),analysis,new PdfExportService(analysis));
        var mvc=MockMvcBuilders.standaloneSetup(new SemanticController(workflow,semantics,proposals,transactions),new BenchmarkController(new PortfolioBenchmarkService(semantics,proposals,transactions))).setControllerAdvice(new ApiExceptionHandler()).build();
        var mapper=new ObjectMapper();var response=mvc.perform(post("/api/demos/semantic")).andExpect(status().isOk()).andReturn().getResponse();
        var source=mapper.readTree(response.getContentAsByteArray());String base="/api/documents/"+source.get("id").asText();
        mvc.perform(get(base+"/semantics")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.structure.version").value("semantics-v1"));
        var body=new HashMap<String,Object>(Map.of("originalSha256",source.get("originalSha256").asText(),"semanticVersion","semantics-v1","operations",List.of(Map.of("kind","LANGUAGE","targetId","document","value","en-US","confirmed",true))));
        mvc.perform(post(base+"/repair-transactions").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.evidence.pagesVerified").value(1)).andExpect(jsonPath("$.pdf").isString());
        body.put("operations",Collections.singletonList(null));
        mvc.perform(post(base+"/repair-transactions").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body))).andExpect(status().isBadRequest());
        body.put("operations",List.of(Map.of("kind","TAG_TEXT","targetId","tag-p1-o5","confirmed",true)));
        mvc.perform(post(base+"/repair-transactions").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body))).andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/demos/unknown")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/benchmarks/samples/2")).andExpect(status().isOk()).andExpect(content().contentType("application/pdf"));
        mvc.perform(get("/api/benchmarks/samples/3")).andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/benchmarks/compare").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
    }
}
