package com.brentcrosby.pdfaccessibilityassistant.api;

import com.brentcrosby.pdfaccessibilityassistant.service.DocumentWorkflowService;
import com.brentcrosby.pdfaccessibilityassistant.service.PdfArtifactRepairService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents/{id}/artifact-repairs")
public class ArtifactRepairController {
    private final DocumentWorkflowService workflow;
    private final PdfArtifactRepairService repairs;
    public ArtifactRepairController(DocumentWorkflowService workflow, PdfArtifactRepairService repairs) {
        this.workflow = workflow; this.repairs = repairs;
    }
    @PostMapping("/check")
    public ResponseEntity<PdfArtifactRepairService.Eligibility> check(@PathVariable UUID id,
            @Valid @RequestBody PdfArtifactRepairService.Request request) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store").body(repairs.check(workflow.document(id),request));
    }
    @PostMapping
    public ResponseEntity<PdfArtifactRepairService.Result> repair(@PathVariable UUID id,
            @Valid @RequestBody PdfArtifactRepairService.Request request) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store").body(repairs.repair(workflow.document(id),request));
    }
}
