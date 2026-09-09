package com.brentcrosby.pdfaccessibilityassistant.service;

import com.brentcrosby.pdfaccessibilityassistant.domain.StoredDocument;
import jakarta.validation.constraints.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import java.io.*;
import java.security.*;
import java.time.Instant;
import java.util.*;

@Service
public class PdfArtifactRepairService {
    public record Request(@Min(1) int pageNumber, @NotBlank @Size(max=80) String regionId,
                          @NotBlank @Size(max=64) String originalSha256,
                          @NotBlank @Size(max=80) String geometryVersion, boolean decorativeConfirmed,
                          @Size(max=1000) String note) {}
    public record Eligibility(boolean eligible, String reason, PdfObservationService.Region region) {}
    public record Evidence(String schemaVersion, String action, String originalFilename, String originalSha256,
                           String outputSha256, String outputFilename, Instant repairedAt,
                           PdfObservationService.Region region, String geometryVersion, String reviewerNote,
                           boolean decorativeConfirmed, boolean appliedToPdf, Map<String,Boolean> verification,
                           String preservedDocumentDigest, String limitation) {}
    public record Result(byte[] pdf, byte[] beforePreview, byte[] afterPreview, Evidence evidence) {}
    private final PdfObservationService observations;
    public PdfArtifactRepairService(PdfObservationService observations) { this.observations = observations; }

    public Eligibility check(StoredDocument stored, Request request) {
        validateIdentity(stored, request);
        try (var doc = Loader.loadPDF(stored.originalBytes())) {
            guardDocument(doc);
            var page = observations.inspect(stored.originalBytes(), request.pageNumber());
            if (page.truncated()) return new Eligibility(false,"This page has partial observations and cannot be repaired.",null);
            var region = page.regions().stream().filter(r -> r.id().equals(request.regionId())).findFirst()
                    .orElseThrow(() -> new PdfInputException("The selected region no longer matches this source. Select it again."));
            if (!region.kind().equals("PATH")) return new Eligibility(false,"This repair supports decorative paths only.",region);
            if (region.sourceOperatorIndex() < 0) return new Eligibility(false,"This path is inside a reusable form. Form repairs are not supported yet.",region);
            var program = new PathRepairProgram(doc.getPage(request.pageNumber()-1));
            var span = program.target(region.sourceOperatorIndex());
            return new Eligibility(span.eligible(), span.eligible() ? "This drawing can be marked as an artifact. Confirm that it is decorative and conveys no information." : span.refusal(), region);
        } catch (IOException ex) { throw new PdfInputException("This PDF could not be checked for artifact repair.",ex); }
    }

    public Result repair(StoredDocument stored, Request request) {
        if (!request.decorativeConfirmed()) throw new PdfInputException("Confirm that the selected path is decorative before exporting an artifact repair.");
        var eligibility = check(stored, request);
        if (!eligibility.eligible()) throw new PdfInputException(eligibility.reason());
        byte[] original = stored.originalBytes(), result, expectedTokens;
        String beforeGraph, beforeText;
        byte[] beforePreview = observations.render(original,request.pageNumber());
        try (var doc = Loader.loadPDF(original); var output = new ByteArrayOutputStream()) {
            beforeGraph = PdfPreservationDigest.of(doc,request.pageNumber());
            beforeText = pageText(doc,request.pageNumber());
            var page = doc.getPage(request.pageNumber()-1);
            var program = new PathRepairProgram(page);
            expectedTokens = program.withArtifact(program.target(eligibility.region().sourceOperatorIndex()));
            // Replace this page's Contents reference; never mutate a stream shared by other pages.
            page.setContents(new PDStream(doc,new ByteArrayInputStream(expectedTokens),COSName.FLATE_DECODE));
            doc.save(output); result = output.toByteArray();
        } catch (IOException ex) { throw new PdfInputException("The artifact copy could not be written.",ex); }
        if (result.length > 10 * 1024 * 1024) throw new PdfInputException("The repaired copy exceeds the 10 MB local intake limit.");
        try (var reopened = Loader.loadPDF(result)) {
            String afterGraph = PdfPreservationDigest.of(reopened,request.pageNumber());
            byte[] actualTokens = PathRepairProgram.canonical(new PathRepairProgram(reopened.getPage(request.pageNumber()-1)).tokens);
            if (!beforeGraph.equals(afterGraph) || !beforeText.equals(pageText(reopened,request.pageNumber())) || !Arrays.equals(expectedTokens,actualTokens))
                throw new PdfInputException("Artifact verification failed: document structure, text or content instructions changed unexpectedly. No copy was returned.");
        } catch (IOException ex) { throw new PdfInputException("The artifact copy could not be reopened and verified.",ex); }
        byte[] afterPreview = observations.render(result,request.pageNumber());
        try {
            if (!samePixels(beforePreview,afterPreview)) throw new PdfInputException("Artifact verification failed: page appearance changed. No copy was returned.");
        } catch (IOException ex) { throw new PdfInputException("The repair previews could not be compared.",ex); }
        if (!stored.sha256().equals(sha256(stored.originalBytes()))) throw new PdfInputException("Original-byte verification failed.");
        String filename = stored.originalFilename().replaceFirst("(?i)\\.pdf$","") + "-artifact.pdf";
        var evidence = new Evidence("1.0","DECORATIVE_PATH_ARTIFACT_APPLIED",stored.originalFilename(),stored.sha256(),
                sha256(result),filename,Instant.now(),eligibility.region(),PdfObservationService.GEOMETRY_VERSION,
                request.note() == null ? "" : request.note().strip(),true,true,
                Map.of("exactExpectedContentTokens",true,"catalogAndInfoGraphPreserved",true,"selectedPageTextUnchanged",true,
                        "selectedPagePreviewPixelsUnchanged",true,"originalBytesUnchanged",true), beforeGraph,
                "One reviewer-confirmed decorative path was wrapped in Artifact marked content. Pixel comparison uses the bounded page preview; decorative meaning and accessibility conformance are not established by these checks. Other review-plan decisions were not applied.");
        return new Result(result,beforePreview,afterPreview,evidence);
    }
    private static void validateIdentity(StoredDocument stored, Request request) {
        if (!stored.sha256().equals(request.originalSha256()) || !PdfObservationService.GEOMETRY_VERSION.equals(request.geometryVersion()))
            throw new PdfInputException("The repair request refers to a different source or geometry version. Reload and select the path again.");
    }
    private static void guardDocument(PDDocument doc) throws IOException {
        if (doc.isEncrypted()) throw new PdfInputException("Encrypted PDFs are not supported for artifact repair.");
        if (!doc.getSignatureDictionaries().isEmpty() || doc.getDocumentCatalog().getCOSObject().containsKey(COSName.PERMS))
            throw new PdfInputException("Signed or certified PDFs are not supported for artifact repair.");
    }
    private static String pageText(PDDocument doc, int page) throws IOException {
        var stripper = new PDFTextStripper(); stripper.setStartPage(page); stripper.setEndPage(page); return stripper.getText(doc);
    }
    static boolean samePixels(byte[] before, byte[] after) throws IOException {
        var a = ImageIO.read(new ByteArrayInputStream(before)); var b = ImageIO.read(new ByteArrayInputStream(after));
        if (a == null || b == null || a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return false;
        return Arrays.equals(a.getRGB(0,0,a.getWidth(),a.getHeight(),null,0,a.getWidth()), b.getRGB(0,0,b.getWidth(),b.getHeight(),null,0,b.getWidth()));
    }
    static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
