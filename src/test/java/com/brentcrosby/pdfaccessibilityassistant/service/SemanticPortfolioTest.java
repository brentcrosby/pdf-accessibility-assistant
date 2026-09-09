package com.brentcrosby.pdfaccessibilityassistant.service;

import com.brentcrosby.pdfaccessibilityassistant.domain.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class SemanticPortfolioTest {
    final PdfObservationService observations=new PdfObservationService();
    final PdfSemanticService semantics=new PdfSemanticService(observations);
    final TagProposalService proposals=new TagProposalService();
    final PdfTransactionService transactions=new PdfTransactionService(observations,semantics,proposals);
    StoredDocument source(byte[] bytes){return new StoredDocument(UUID.randomUUID(),"demo.pdf",SourceType.SYNTHETIC,bytes,PdfArtifactRepairService.sha256(bytes),Instant.now());}
    PdfTransactionService.Operation op(String kind,String target,String value,List<String> order,String parent){return new PdfTransactionService.Operation(kind,target,1,value,order,true,parent);}
    PdfTransactionService.Result apply(StoredDocument source,List<PdfTransactionService.Operation> ops){return transactions.export(source,new PdfTransactionService.Request(source.sha256(),PdfSemanticService.VERSION,ops));}
    @Test void mapsNamedPropertiesThroughStructureAndParentTreesAndDistinguishesArtifactsAndUntaggedContent() {
        var snapshot=semantics.inspect(PortfolioSamples.semantic());
        assertThat(snapshot.nodes()).allSatisfy(n->assertThat(n.issues()).isEmpty());
        assertThat(snapshot.regions()).extracting(PdfSemanticService.Membership::status).contains("TAGGED","ARTIFACT","UNTAGGED");
        assertThat(snapshot.regions().stream().filter(m->m.region().kind().equals("IMAGE"))).singleElement().satisfies(m->{assertThat(m.role()).isEqualTo("Figure");assertThat(m.status()).isEqualTo("TAGGED");});
        assertThat(snapshot.nodes().stream().filter(PdfSemanticService.Node::altEditable)).singleElement().satisfies(n->{assertThat(n.altText()).isNull();assertThat(n.locations()).hasSize(1);});
        assertThat(snapshot.nodes().stream().filter(PdfSemanticService.Node::reorderable)).singleElement();
    }
    @Test void combinedTransactionWritesAltOrderArtifactAndLanguageAndPreservesSourceAndPixels() {
        var source=source(PortfolioSamples.semantic());var snapshot=semantics.inspect(source.originalBytes());
        var figure=snapshot.nodes().stream().filter(PdfSemanticService.Node::altEditable).findFirst().orElseThrow();
        var parent=snapshot.nodes().stream().filter(PdfSemanticService.Node::reorderable).findFirst().orElseThrow();
        var order=new ArrayList<>(parent.childIds());Collections.swap(order,0,1);
        var path=snapshot.regions().stream().filter(m->m.region().kind().equals("PATH")).findFirst().orElseThrow();
        var result=apply(source,List.of(op("ALT_TEXT",figure.id(),"Participation rose from 30 to 48 to 70 people.",null,null),op("READING_ORDER",parent.id(),null,order,null),
                op("ARTIFACT",path.region().id(),null,null,null),op("LANGUAGE","document","en-US",null,null),op("DISPLAY_TITLE","document",null,null,null)));
        assertThat(result.evidence().appliedActions()).hasSize(5);assertThat(result.evidence().checks().values()).containsOnly(true);
        assertThat(PdfArtifactRepairService.sha256(source.originalBytes())).isEqualTo(source.sha256());
        var after=semantics.inspect(result.pdf());
        assertThat(after.nodes().stream().filter(PdfSemanticService.Node::altEditable).findFirst().orElseThrow().altText()).contains("30 to 48 to 70");
        var first=after.nodes().stream().filter(n->n.parentId()!=null && n.parentId().equals(parent.id())).findFirst().orElseThrow();assertThat(first.role()).isEqualTo("H1");
        assertThat(after.regions().stream().filter(m->m.region().id().equals(path.region().id())).findFirst().orElseThrow().status()).isEqualTo("ARTIFACT");
    }
    @Test void acceptsTextGroupsAndCreatesValidStructureAndParentTreeThenAllowsReadingOrder() {
        var source=source(PortfolioSamples.untagged(0).pdf());var proposal=proposals.propose(semantics.inspect(source.originalBytes()));
        assertThat(proposal).hasSize(7);assertThat(proposal).extracting(TagProposalService.Proposal::role).contains("H1","H2","LI","Caption","P");
        var result=apply(source,proposal.stream().map(p->op("TAG_TEXT",p.id(),p.role(),null,"auto")).toList());
        var after=semantics.inspect(result.pdf());assertThat(after.nodes()).allSatisfy(n->assertThat(n.issues()).isEmpty());
        assertThat(after.regions().stream().filter(m->m.region().kind().equals("TEXT"))).allSatisfy(m->assertThat(m.status()).isEqualTo("TAGGED"));
        assertThat(after.nodes()).extracting(PdfSemanticService.Node::role).contains("L","LI","LBody");
        var parent=after.nodes().stream().filter(n->n.role().equals("Document")).findFirst().orElseThrow();var reversed=new ArrayList<>(parent.childIds());Collections.reverse(reversed);
        var ordered=apply(source(result.pdf()),List.of(op("READING_ORDER",parent.id(),null,reversed,null)));assertThat(ordered.evidence().checks().values()).containsOnly(true);
    }
    @Test void canTagUnmarkedTextAlongsideExistingFiguresWithoutDroppingOldParentEntries() {
        var source=source(PortfolioSamples.semantic());var snapshot=semantics.inspect(source.originalBytes());var proposal=proposals.propose(snapshot).getFirst();
        var parent=snapshot.nodes().stream().filter(n->n.role().equals("Document")).findFirst().orElseThrow();
        var result=apply(source,List.of(op("TAG_TEXT",proposal.id(),"P",null,parent.id())));
        var after=semantics.inspect(result.pdf());assertThat(after.nodes()).allSatisfy(n->assertThat(n.issues()).isEmpty());
        assertThat(after.regions().stream().filter(m->m.region().kind().equals("IMAGE"))).singleElement().satisfies(m->assertThat(m.status()).isEqualTo("TAGGED"));
        assertThat(proposals.propose(after)).isEmpty();
    }
    @Test void invalidOrderAndConflictingRepairsRollBackWithoutChangingTheSource() {
        var source=source(PortfolioSamples.semantic());byte[] original=source.originalBytes();var snapshot=semantics.inspect(original);
        var figure=snapshot.nodes().stream().filter(PdfSemanticService.Node::altEditable).findFirst().orElseThrow();var parent=snapshot.nodes().stream().filter(PdfSemanticService.Node::reorderable).findFirst().orElseThrow();
        assertThatThrownBy(()->apply(source,List.of(op("ALT_TEXT",figure.id(),"Example alt",null,null),op("READING_ORDER",parent.id(),null,List.of(parent.childIds().getFirst()),null)))).hasMessageContaining("every existing sibling");
        var p=proposals.propose(snapshot).getFirst();
        assertThatThrownBy(()->apply(source,List.of(op("TAG_TEXT",p.id(),"P",null,parent.id()),op("READING_ORDER",parent.id(),null,parent.childIds(),null)))).hasMessageContaining("separate exports");
        assertThat(source.originalBytes()).isEqualTo(original);
        assertThatThrownBy(()->transactions.export(source,new PdfTransactionService.Request("wrong",PdfSemanticService.VERSION,List.of(op("LANGUAGE","document","en",null,null))))).hasMessageContaining("different source");
    }
    @Test void brokenParentTreeAndRoleMapCyclesAreUnresolvedAndCannotReceiveAltRepairs()throws Exception {
        byte[] malformed;
        try(var doc=Loader.loadPDF(PortfolioSamples.semantic());var out=new ByteArrayOutputStream()) {
            var index=new PdfStructureIndex(doc);var figure=index.entries.values().stream().filter(e->e.role.equals("Figure")).findFirst().orElseThrow();
            ((COSArray)index.parentTree.get(0)).set(2,COSNull.NULL);var roles=new COSDictionary();roles.setName(COSName.getPDFName("CycleA"),"CycleB");roles.setName(COSName.getPDFName("CycleB"),"CycleA");index.root.setItem(COSName.ROLE_MAP,roles);figure.dictionary.setName(COSName.S,"CycleA");doc.save(out);malformed=out.toByteArray();
        }
        var snapshot=semantics.inspect(malformed);assertThat(snapshot.nodes()).noneMatch(PdfSemanticService.Node::altEditable);
        assertThat(snapshot.regions()).anySatisfy(m->assertThat(m.status()).isEqualTo("UNRESOLVED"));
        assertThatThrownBy(()->apply(source(malformed),List.of(op("ALT_TEXT","s4","wrong",null,null)))).hasMessageContaining("unresolved issues");
    }
    @Test void roleMappedFiguresAndExplicitPageMcrReferencesResolve()throws Exception {
        byte[] bytes;
        try(var doc=Loader.loadPDF(PortfolioSamples.semantic());var out=new ByteArrayOutputStream()) {
            var index=new PdfStructureIndex(doc);var figure=index.entries.values().stream().filter(e->e.role.equals("Figure")).findFirst().orElseThrow();
            var roles=new COSDictionary();roles.setName(COSName.getPDFName("Chart"),"Figure");index.root.setItem(COSName.ROLE_MAP,roles);figure.dictionary.setName(COSName.S,"Chart");
            var ref=new COSDictionary();ref.setItem(COSName.TYPE,COSName.MCR);ref.setInt(COSName.MCID,2);ref.setItem(COSName.PG,doc.getPage(0));figure.dictionary.setItem(COSName.K,ref);doc.save(out);bytes=out.toByteArray();
        }
        assertThat(semantics.inspect(bytes).nodes().stream().filter(PdfSemanticService.Node::altEditable)).singleElement().satisfies(n->assertThat(n.originalRole()).isEqualTo("Chart"));
    }
    @Test void reusedMcidSequenceDoesNotSilentlyMapToTheSameTag()throws Exception {
        byte[] bytes;
        try(var doc=Loader.loadPDF(PortfolioSamples.semantic());var out=new ByteArrayOutputStream()) {
            var page=doc.getPage(0);byte[] original=page.getContents().readAllBytes();var content=new ByteArrayOutputStream();content.write(original);content.write("\n/Figure << /MCID 2 >> BDC 10 10 20 20 re f EMC\n".getBytes());page.setContents(new PDStream(doc,new ByteArrayInputStream(content.toByteArray())));doc.save(out);bytes=out.toByteArray();
        }
        assertThat(semantics.inspect(bytes).regions().stream().filter(m->Objects.equals(m.region().mcid(),2))).allSatisfy(m->assertThat(m.status()).isEqualTo("UNRESOLVED"));
    }
    @Test void benchmarkIsReproducibleAndReportsKnownFailuresAndValidatedComparison() {
        assertThat(PortfolioSamples.untagged(0).pdf()).isEqualTo(PortfolioSamples.untagged(0).pdf());
        var benchmark=new PortfolioBenchmarkService(semantics,proposals,transactions);var report=benchmark.run(null);
        assertThat(report.metrics().total()).isEqualTo(14);assertThat(report.metrics().correct()).isLessThan(14);assertThat(report.metrics().recall()).isGreaterThan(report.paragraphBaseline().recall());
        assertThat(report.repairChecks().values()).containsOnly(true);assertThat(benchmark.run(report.comparisonTemplate()).comparison()).isEqualTo(report.metrics());
        assertThatThrownBy(()->benchmark.run(new PortfolioBenchmarkService.Comparison("other","wrong",Map.of(),Map.of()))).hasMessageContaining("corpus version");
    }
    @Test void multipageTaggingAllocatesSeparateParentKeyAndPreservesExistingPageReferences() throws Exception {
        byte[] bytes;
        try(var doc=Loader.loadPDF(PortfolioSamples.semantic());var second=Loader.loadPDF(PortfolioSamples.untagged(0).pdf());var out=new ByteArrayOutputStream()){
            doc.importPage(second.getPage(0));doc.save(out);bytes=out.toByteArray();
        }
        var source=source(bytes);var snapshot=semantics.inspect(bytes);var parent=snapshot.nodes().stream().filter(PdfSemanticService.Node::appendEligible).findFirst().orElseThrow();
        var accepted=proposals.propose(snapshot).stream().filter(p->p.pageNumber()==2).toList();
        var result=apply(source,accepted.stream().map(p->op("TAG_TEXT",p.id(),p.role(),null,parent.id())).toList());
        assertThat(result.evidence().pagesVerified()).isEqualTo(2);
        assertThat(semantics.inspect(result.pdf()).nodes()).allSatisfy(n->assertThat(n.issues()).isEmpty());
        try(var doc=Loader.loadPDF(result.pdf())){
            assertThat(doc.getPage(0).getCOSObject().getInt(COSName.STRUCT_PARENTS)).isEqualTo(0);
            assertThat(doc.getPage(1).getCOSObject().getInt(COSName.STRUCT_PARENTS)).isEqualTo(1);
        }
    }
    @Test void malformedNestedFormCannotLeakMarkedContentIntoFollowingPageText() throws Exception {
        byte[] bytes;
        try(var doc=Loader.loadPDF(PortfolioSamples.untagged(0).pdf());var out=new ByteArrayOutputStream()){
            var page=doc.getPage(0);var form=new org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject(doc);
            form.setBBox(new org.apache.pdfbox.pdmodel.common.PDRectangle(100,100));form.setResources(new org.apache.pdfbox.pdmodel.PDResources());
            try(var stream=form.getCOSObject().createOutputStream()){stream.write("/Artifact BMC 0 0 10 10 re f".getBytes());}
            page.getResources().put(COSName.getPDFName("MalformedForm"),form);
            var content=new ByteArrayOutputStream();content.write("/MalformedForm Do\n".getBytes());content.write(page.getContents().readAllBytes());
            page.setContents(new PDStream(doc,new ByteArrayInputStream(content.toByteArray())));doc.save(out);bytes=out.toByteArray();
        }
        var raw=observations.inspect(bytes,1);assertThat(raw.warnings()).anyMatch(w->w.startsWith("Malformed marked content"));
        assertThat(raw.regions().stream().filter(r->r.kind().equals("TEXT"))).allSatisfy(r->assertThat(r.artifact()).isFalse());
        assertThat(semantics.inspect(bytes).regions()).allSatisfy(m->assertThat(m.status()).isEqualTo("UNRESOLVED"));
        assertThat(proposals.propose(semantics.inspect(bytes))).isEmpty();
    }
    @Test void invalidActionsNullRolesAndDuplicateMetadataAreRefusedWithoutMutation() {
        var source=source(PortfolioSamples.untagged(0).pdf());var p=proposals.propose(semantics.inspect(source.originalBytes())).getFirst();
        assertThatThrownBy(()->apply(source,List.of(op("TAG_TEXT",p.id(),null,null,"auto")))).hasMessageContaining("supported text role");
        assertThatThrownBy(()->apply(source,List.of(op("LANGUAGE","a","en",null,null),op("LANGUAGE","b","fr",null,null)))).hasMessageContaining("Duplicate");
        assertThatThrownBy(()->apply(source,List.of(new PdfTransactionService.Operation("TAG_TEXT",p.id(),1,"P",null,false,"auto")))).hasMessageContaining("accepted");
        assertThatThrownBy(()->apply(source,List.of(op("DELETE_TEXT",p.id(),null,null,null)))).hasMessageContaining("Unsupported");
        assertThat(PdfArtifactRepairService.sha256(source.originalBytes())).isEqualTo(source.sha256());
    }
    @Test void signedAndOverLimitDocumentsAreRefused()throws Exception {
        byte[] signed,oversize;
        try(var doc=Loader.loadPDF(PortfolioSamples.semantic());var out=new ByteArrayOutputStream()){
            doc.getDocumentCatalog().getCOSObject().setItem(COSName.PERMS,new COSDictionary());doc.save(out);signed=out.toByteArray();
        }
        assertThatThrownBy(()->apply(source(signed),List.of(op("LANGUAGE","document","en",null,null)))).hasMessageContaining("certified");
        try(var doc=Loader.loadPDF(PortfolioSamples.semantic());var out=new ByteArrayOutputStream()){
            for(int i=0;i<20;i++)doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());doc.save(out);oversize=out.toByteArray();
        }
        assertThatThrownBy(()->semantics.inspect(oversize)).hasMessageContaining("20 pages");
    }
    @Test void comparisonAbstentionsAndFalseArtifactPredictionsAreMeasured() {
        var benchmark=new PortfolioBenchmarkService(semantics,proposals,transactions);var baseline=benchmark.run(null);
        var report=benchmark.run(new PortfolioBenchmarkService.Comparison("Manual test",baseline.corpusVersion(),baseline.sourceHashes(),Map.of(baseline.rows().getFirst().id(),"ARTIFACT")));
        assertThat(report.comparison().abstained()).isEqualTo(13);assertThat(report.comparison().falseArtifactPredictions()).isEqualTo(1);assertThat(report.comparison().correct()).isZero();
        var invalid=new HashMap<String,String>();invalid.put(baseline.rows().getFirst().id(),null);
        assertThatThrownBy(()->benchmark.run(new PortfolioBenchmarkService.Comparison("Invalid",baseline.corpusVersion(),baseline.sourceHashes(),invalid))).hasMessageContaining("roles are invalid");
    }
}
