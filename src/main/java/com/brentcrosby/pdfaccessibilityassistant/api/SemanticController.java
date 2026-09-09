package com.brentcrosby.pdfaccessibilityassistant.api;

import com.brentcrosby.pdfaccessibilityassistant.domain.SourceType;
import com.brentcrosby.pdfaccessibilityassistant.service.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class SemanticController {
    private final DocumentWorkflowService workflow;private final PdfSemanticService semantics;private final TagProposalService proposals;private final PdfTransactionService transactions;
    public SemanticController(DocumentWorkflowService workflow,PdfSemanticService semantics,TagProposalService proposals,PdfTransactionService transactions){this.workflow=workflow;this.semantics=semantics;this.proposals=proposals;this.transactions=transactions;}
    public record Review(PdfSemanticService.Snapshot structure,List<TagProposalService.Proposal> proposals) {}
    @GetMapping("/documents/{id}/semantics") public ResponseEntity<Review> inspect(@PathVariable UUID id) {
        var snapshot=semantics.inspect(workflow.document(id).originalBytes());return ResponseEntity.ok().header("Cache-Control","no-store").body(new Review(snapshot,proposals.propose(snapshot)));
    }
    @PostMapping("/documents/{id}/repair-transactions") public ResponseEntity<PdfTransactionService.Result> repair(@PathVariable UUID id,@Valid @RequestBody PdfTransactionService.Request request) {
        return ResponseEntity.ok().header("Cache-Control","no-store").body(transactions.export(workflow.document(id),request));
    }
    @PostMapping("/demos/{name}") public DocumentResponse demo(@PathVariable String name) {
        if(!Set.of("semantic","untagged").contains(name))throw new PdfInputException("Unknown demo document.");
        byte[] bytes=name.equals("semantic")?PortfolioSamples.semantic():PortfolioSamples.untagged(0).pdf();var doc=workflow.upload(name+"-demo.pdf",SourceType.SYNTHETIC,bytes);
        return DocumentResponse.from(doc,workflow.analysis(doc.id()));
    }
}
