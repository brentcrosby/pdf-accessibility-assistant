package com.brentcrosby.pdfaccessibilityassistant.api;

import com.brentcrosby.pdfaccessibilityassistant.domain.ReviewDecision;
import com.brentcrosby.pdfaccessibilityassistant.domain.SourceType;
import com.brentcrosby.pdfaccessibilityassistant.domain.StoredDocument;
import com.brentcrosby.pdfaccessibilityassistant.service.DocumentWorkflowService;
import com.brentcrosby.pdfaccessibilityassistant.service.PdfExportService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    private final DocumentWorkflowService workflowService;

    public DocumentController(DocumentWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam SourceType sourceType
    ) throws IOException {
        StoredDocument document = workflowService.upload(file.getOriginalFilename(), sourceType, file.getBytes());
        return ResponseEntity.status(HttpStatus.CREATED).body(response(document));
    }

    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable UUID id) {
        return response(workflowService.document(id));
    }

    @PostMapping("/{id}/reviews")
    public DocumentResponse review(@PathVariable UUID id, @Valid @org.springframework.web.bind.annotation.RequestBody ReviewRequest request) {
        StoredDocument document = workflowService.recordReview(id, request.issueCode(), request.decision(), request.value());
        return response(document);
    }

    @PostMapping(value = "/{id}/export", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> export(@PathVariable UUID id) {
        StoredDocument document = workflowService.document(id);
        PdfExportService.ExportedPdf exportedPdf = workflowService.export(id);
        String filename = exportFilename(document.originalFilename());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
        headers.set("X-Remediation-Actions", String.join(",", exportedPdf.appliedActions()));
        headers.set("X-Revalidation-Issues", String.valueOf(exportedPdf.reanalysis().issues().size()));
        return new ResponseEntity<>(exportedPdf.bytes(), headers, HttpStatus.OK);
    }

    private DocumentResponse response(StoredDocument document) {
        return DocumentResponse.from(document, workflowService.analysis(document.id()));
    }

    private String exportFilename(String originalFilename) {
        String baseName = originalFilename.replaceFirst("(?i)\\.pdf$", "");
        return baseName + "-remediated.pdf";
    }
}
