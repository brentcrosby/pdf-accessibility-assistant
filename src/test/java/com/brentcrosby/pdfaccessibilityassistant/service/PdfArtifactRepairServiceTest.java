package com.brentcrosby.pdfaccessibilityassistant.service;

import com.brentcrosby.pdfaccessibilityassistant.domain.*;
import com.brentcrosby.pdfaccessibilityassistant.support.WorkbenchFixture;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.*;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PdfArtifactRepairServiceTest {
    private final PdfObservationService observations = new PdfObservationService();
    private final PdfArtifactRepairService repairs = new PdfArtifactRepairService(observations);
    static StoredDocument stored(byte[] bytes) {
        return new StoredDocument(UUID.randomUUID(),"synthetic.pdf",SourceType.SYNTHETIC,bytes,PdfArtifactRepairService.sha256(bytes),Instant.now());
    }
    private PdfArtifactRepairService.Request request(StoredDocument source, int page, String id) {
        return new PdfArtifactRepairService.Request(page,id,source.sha256(),PdfObservationService.GEOMETRY_VERSION,true,"Decorative rule");
    }
    private String path(StoredDocument source, int page, int index) {
        return observations.inspect(source.originalBytes(),page).regions().stream().filter(r->r.kind().equals("PATH")).toList().get(index).id();
    }
    @ParameterizedTest @ValueSource(ints={0,90,180,270})
    void exportsAnActualArtifactAtEveryRotationAndPreservesTextPixelsAndOtherPageStreams(int rotation) throws Exception {
        byte[] bytes;
        try (var doc = Loader.loadPDF(WorkbenchFixture.create()); var out = new ByteArrayOutputStream()) {
            doc.getPage(0).setRotation(rotation); doc.getPage(0).setCropBox(new PDRectangle(20,40,540,640));
            doc.save(out); bytes = out.toByteArray();
        }
        var source = stored(bytes); var request = request(source,1,path(source,1,0));
        assertThat(repairs.check(source,request).eligible()).isTrue();
        var result = repairs.repair(source,request);
        assertThat(result.pdf()).isNotEqualTo(bytes);
        assertThat(source.originalBytes()).isEqualTo(bytes);
        assertThat(result.evidence().outputSha256()).isEqualTo(PdfArtifactRepairService.sha256(result.pdf()));
        assertThat(result.evidence().verification().values()).containsOnly(true);
        assertThat(result.evidence().appliedToPdf()).isTrue();
        assertThat(PdfArtifactRepairService.samePixels(result.beforePreview(),result.afterPreview())).isTrue();
        try (var before = Loader.loadPDF(bytes); var after = Loader.loadPDF(result.pdf())) {
            String stream = new String(after.getPage(0).getContents().readAllBytes(),StandardCharsets.ISO_8859_1);
            assertThat(stream).contains("/Artifact BMC").contains("EMC");
            for (int p=1;p<4;p++) assertThat(after.getPage(p).getContents().readAllBytes()).isEqualTo(before.getPage(p).getContents().readAllBytes());
        }
        var reopened = stored(result.pdf());
        assertThat(repairs.check(reopened,request(reopened,1,path(reopened,1,0))).reason()).contains("already");
        // A second independently selected path can be repaired on the prior output.
        var second = repairs.repair(reopened,request(reopened,1,path(reopened,1,1)));
        try (var doc = Loader.loadPDF(second.pdf())) {
            assertThat(new String(doc.getPage(0).getContents().readAllBytes(),StandardCharsets.ISO_8859_1).split("/Artifact BMC",-1)).hasSize(3);
        }
    }

    @Test void rejectsWrongSourceVersionUnknownRegionTextAndUnconfirmedRequests() throws Exception {
        var source = stored(WorkbenchFixture.create()); var id = path(source,1,0);
        assertThatThrownBy(()->repairs.repair(source,new PdfArtifactRepairService.Request(1,id,source.sha256(),PdfObservationService.GEOMETRY_VERSION,false,""))).hasMessageContaining("Confirm");
        assertThatThrownBy(()->repairs.check(source,new PdfArtifactRepairService.Request(1,id,"0".repeat(64),PdfObservationService.GEOMETRY_VERSION,true,""))).hasMessageContaining("different source");
        assertThatThrownBy(()->repairs.check(source,new PdfArtifactRepairService.Request(1,id,source.sha256(),"old",true,""))).hasMessageContaining("geometry");
        assertThatThrownBy(()->repairs.check(source,request(source,1,"p1-o999"))).hasMessageContaining("no longer matches");
        assertThat(repairs.check(source,request(source,1,"p1-o1")).eligible()).isFalse();
        assertThatThrownBy(()->repairs.repair(source,request(source,1,"p1-o1"))).hasMessageContaining("paths only");
    }

    @ParameterizedTest @ValueSource(strings={"/Artifact BMC 10 10 50 50 re f EMC", "/P <</MCID 0>> BDC 10 10 50 50 re f EMC", "10 10 50 50 re W f", "10 10 m 60 60 l 2 w S"})
    void refusesMarkedClippingAndInterleavedPaths(String content) throws Exception {
        var source = stored(raw(content,false));
        var req = request(source,1,path(source,1,0));
        assertThat(repairs.check(source,req).eligible()).isFalse();
        assertThatThrownBy(()->repairs.repair(source,req)).isInstanceOf(PdfInputException.class);
    }

    @Test void preservesExistingStructureTreeAndParentTreeWhenRepairingAnUnmarkedPath() throws Exception {
        var source = stored(raw("/P <</MCID 0>> BDC 10 10 50 50 re f EMC 80 80 40 40 re f",true));
        assertThat(repairs.check(source,request(source,1,path(source,1,0))).eligible()).isFalse();
        var result = repairs.repair(source,request(source,1,path(source,1,1)));
        try (var doc = Loader.loadPDF(result.pdf())) {
            var root = doc.getDocumentCatalog().getCOSObject().getCOSDictionary(COSName.STRUCT_TREE_ROOT);
            assertThat(root).isNotNull(); assertThat(root.getCOSDictionary(COSName.PARENT_TREE)).isNotNull();
            assertThat(doc.getPage(0).getCOSObject().getInt(COSName.STRUCT_PARENTS)).isEqualTo(0);
            String content = new String(doc.getPage(0).getContents().readAllBytes(),StandardCharsets.ISO_8859_1);
            assertThat(content).contains("/MCID 0").contains("/Artifact BMC");
        }
    }

    @Test void refusesNestedFormButMapsAFollowingDirectPathCorrectly() throws Exception {
        byte[] bytes;
        try(var doc = new PDDocument(); var out = new ByteArrayOutputStream()) {
            var page = new PDPage(); doc.addPage(page);
            var form = new PDFormXObject(doc); form.setBBox(new PDRectangle(100,100)); form.setResources(new PDResources());
            try(var stream = new PDFormContentStream(form)) { stream.addRect(10,10,20,20); stream.fill(); }
            try(var stream = new PDPageContentStream(doc,page)) { stream.drawForm(form); stream.addRect(100,100,30,30); stream.fill(); }
            doc.save(out); bytes = out.toByteArray();
        }
        var source = stored(bytes);
        assertThat(repairs.check(source,request(source,1,path(source,1,0))).reason()).contains("reusable form");
        var result = repairs.repair(source,request(source,1,path(source,1,1)));
        assertThat(result.evidence().verification().values()).containsOnly(true);
    }

    @Test void doesNotModifyAContentsStreamSharedWithAnotherPage() throws Exception {
        byte[] bytes;
        try (var doc = Loader.loadPDF(raw("10 10 50 50 re f",false)); var out = new ByteArrayOutputStream()) {
            var second = new PDPage(); second.getCOSObject().setItem(COSName.CONTENTS,doc.getPage(0).getCOSObject().getItem(COSName.CONTENTS)); doc.addPage(second);
            doc.save(out); bytes = out.toByteArray();
        }
        var source = stored(bytes); var result = repairs.repair(source,request(source,1,path(source,1,0)));
        try(var doc = Loader.loadPDF(result.pdf())) {
            assertThat(new String(doc.getPage(1).getContents().readAllBytes(),StandardCharsets.US_ASCII)).doesNotContain("Artifact");
        }
    }

    @Test void refusesMalformedAndCertifiedDocuments() throws Exception {
        var malformed = stored(raw("q 10 10 50 50 re f",false));
        assertThatThrownBy(()->repairs.check(malformed,request(malformed,1,path(malformed,1,0)))).hasMessageContaining("unbalanced");
        byte[] bytes;
        try(var doc = Loader.loadPDF(raw("10 10 50 50 re f",false)); var out = new ByteArrayOutputStream()) {
            doc.getDocumentCatalog().getCOSObject().setItem(COSName.PERMS,new COSDictionary()); doc.save(out); bytes = out.toByteArray();
        }
        var certified = stored(bytes);
        assertThatThrownBy(()->repairs.check(certified,request(certified,1,"p1-o1"))).hasMessageContaining("certified");
    }

    @Test void preservationDigestDetectsChangesToOtherContentAndStructure() throws Exception {
        try(var doc = Loader.loadPDF(raw("10 10 50 50 re f",true))) {
            var original = PdfPreservationDigest.of(doc,1);
            doc.getDocumentCatalog().setLanguage("fr");
            assertThat(PdfPreservationDigest.of(doc,1)).isNotEqualTo(original);
        }
    }

    @Test void refusesPagesWithInlineImagesBeforeWriting() throws Exception {
        var source = stored(raw("q 30 0 0 30 100 100 cm BI /W 1 /H 1 /BPC 8 /CS /G ID # EI Q 10 10 50 50 re f",false));
        assertThatThrownBy(()->repairs.check(source,request(source,1,path(source,1,0)))).hasMessageContaining("inline image");
        assertThatThrownBy(()->repairs.repair(source,request(source,1,path(source,1,0)))).hasMessageContaining("inline image");
    }

    @Test void refusesToReturnACopyWhenTheRenderedComparisonFails() throws Exception {
        var source = stored(raw("10 10 50 50 re f",false));
        byte[] different = observations.render(raw("20 20 60 60 re f",false),1);
        var injectedRenderer = new PdfObservationService() {
            private int renders;
            @Override public byte[] render(byte[] bytes, int page) { return ++renders == 2 ? different : super.render(bytes,page); }
        };
        assertThatThrownBy(()->new PdfArtifactRepairService(injectedRenderer).repair(source,request(source,1,path(source,1,0))))
                .isInstanceOf(PdfInputException.class).hasMessageContaining("appearance changed");
    }

    @Test void metadataExportOnARepairedCopyRetainsTheArtifact() throws Exception {
        var source = stored(WorkbenchFixture.create());
        var repaired = repairs.repair(source,request(source,1,path(source,1,0)));
        var metadata = new PdfExportService(new PdfAnalysisService()).export(repaired.pdf(),Map.of());
        assertThat(metadata.appliedActions()).contains("DISPLAY_DOCUMENT_TITLE_ENABLED");
        var after = stored(metadata.bytes());
        assertThat(repairs.check(after,request(after,1,path(after,1,0))).reason()).contains("already");
    }

    private byte[] raw(String content, boolean tagged) throws Exception {
        try(var doc = new PDDocument(); var out = new ByteArrayOutputStream()) {
            var page = new PDPage(); doc.addPage(page);
            page.setContents(new PDStream(doc,new ByteArrayInputStream(content.getBytes(StandardCharsets.US_ASCII))));
            if(tagged) {
                var root = new COSDictionary(); root.setItem(COSName.TYPE,COSName.STRUCT_TREE_ROOT);
                var child = new COSDictionary(); child.setItem(COSName.TYPE,COSName.STRUCT_ELEM); child.setItem(COSName.S,COSName.P);
                child.setItem(COSName.P,root); child.setItem(COSName.PG,page); child.setInt(COSName.K,0); root.setItem(COSName.K,child);
                var parentTree = new COSDictionary(); var nums = new COSArray(); nums.add(COSInteger.ZERO);
                var parents = new COSArray(); parents.add(child); nums.add(parents); parentTree.setItem(COSName.NUMS,nums);
                root.setItem(COSName.PARENT_TREE,parentTree); root.setInt(COSName.PARENT_TREE_NEXT_KEY,1);
                doc.getDocumentCatalog().getCOSObject().setItem(COSName.STRUCT_TREE_ROOT,root); page.getCOSObject().setInt(COSName.STRUCT_PARENTS,0);
            }
            doc.save(out); return out.toByteArray();
        }
    }
}
