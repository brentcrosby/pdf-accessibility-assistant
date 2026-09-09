package com.brentcrosby.pdfaccessibilityassistant.support;

import com.brentcrosby.pdfaccessibilityassistant.domain.SourceType;
import com.brentcrosby.pdfaccessibilityassistant.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;

/** Repeatable temporary outputs for independent renderer/browser checks. */
public final class ArtifactRepairExperiment {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !Files.isDirectory(Path.of(args[0]))) throw new IllegalArgumentException("Supply an existing temporary directory.");
        var directory = Path.of(args[0]);
        var analysis = new PdfAnalysisService();
        var workflow = new DocumentWorkflowService(new InMemoryDocumentStore(),analysis,new PdfExportService(analysis));
        var source = workflow.upload("artifact-source.pdf",SourceType.SYNTHETIC,WorkbenchFixture.create());
        var service = new PdfArtifactRepairService(new PdfObservationService());
        var result = service.repair(source,new PdfArtifactRepairService.Request(1,"p1-o4",source.sha256(),PdfObservationService.GEOMETRY_VERSION,true,"Synthetic decorative rule"));
        Files.write(directory.resolve("artifact-source.pdf"),source.originalBytes());
        Files.write(directory.resolve("artifact-repaired.pdf"),result.pdf());
        Files.write(directory.resolve("before.png"),result.beforePreview());
        Files.write(directory.resolve("after.png"),result.afterPreview());
        Files.writeString(directory.resolve("repair-evidence.json"),new ObjectMapper().findAndRegisterModules().writerWithDefaultPrettyPrinter().writeValueAsString(result.evidence()));
        System.out.println("Temporary artifact experiment completed: " + directory);
    }
}
