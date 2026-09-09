package com.brentcrosby.pdfaccessibilityassistant.api;

import com.brentcrosby.pdfaccessibilityassistant.domain.ReviewDecision;
import com.brentcrosby.pdfaccessibilityassistant.domain.SourceType;
import com.brentcrosby.pdfaccessibilityassistant.domain.StoredDocument;
import com.brentcrosby.pdfaccessibilityassistant.service.DocumentWorkflowService;
import com.brentcrosby.pdfaccessibilityassistant.service.PdfExportService;
import com.brentcrosby.pdfaccessibilityassistant.service.PdfTextRegionService;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    private final DocumentWorkflowService workflowService;
    private final PdfTextRegionService textRegionService;

    public DocumentController(DocumentWorkflowService workflowService, PdfTextRegionService textRegionService) {
        this.workflowService = workflowService;
        this.textRegionService = textRegionService;
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

    @GetMapping(value = "/{id}/original", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> originalPreview(@PathVariable UUID id) {
        StoredDocument document = workflowService.document(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.inline()
                .filename(document.originalFilename(), StandardCharsets.UTF_8)
                .build());
        headers.setCacheControl("no-store");
        return new ResponseEntity<>(document.originalBytes(), headers, HttpStatus.OK);
    }

    @GetMapping("/{id}/text-regions")
    public java.util.List<com.brentcrosby.pdfaccessibilityassistant.domain.TextRegion> textRegions(@PathVariable UUID id) {
        return textRegionService.locate(workflowService.document(id).originalBytes());
    }

    @GetMapping(value = "/{id}/pages/{pageNumber}/preview", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> pagePreview(@PathVariable UUID id, @PathVariable int pageNumber) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setCacheControl("no-store");
        return new ResponseEntity<>(textRegionService.renderPage(workflowService.document(id).originalBytes(), pageNumber), headers, HttpStatus.OK);
    }

    @GetMapping("/{id}/pages/{pageNumber}/observations")
    public ResponseEntity<com.brentcrosby.pdfaccessibilityassistant.service.PdfObservationService.PageObservations> observations(
            @PathVariable UUID id, @PathVariable int pageNumber) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(textRegionService.inspectPage(workflowService.document(id).originalBytes(), pageNumber));
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
        headers.set("X-Revalidation-Issue-Codes", exportedPdf.reanalysis().issues().stream()
                .map(issue -> issue.code())
                .collect(Collectors.joining(",")));
        headers.set("X-Revalidation-Page-Count", String.valueOf(exportedPdf.reanalysis().pageCount()));
        headers.set("X-Revalidation-Encrypted", String.valueOf(exportedPdf.reanalysis().encrypted()));
        headers.set("X-Revalidation-Title-Present", String.valueOf(exportedPdf.reanalysis().title() != null));
        headers.set("X-Revalidation-Language-Present", String.valueOf(exportedPdf.reanalysis().language() != null));
        headers.set("X-Revalidation-Display-Document-Title", String.valueOf(exportedPdf.reanalysis().displayDocumentTitle()));
        headers.set("X-Revalidation-Marked", String.valueOf(exportedPdf.reanalysis().markedAsTagged()));
        headers.set("X-Revalidation-Structure-Tree", String.valueOf(exportedPdf.reanalysis().structureTreePresent()));
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
