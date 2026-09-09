import com.brentcrosby.pdfaccessibilityassistant.service.*;
import com.brentcrosby.pdfaccessibilityassistant.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Reproducible evidence runner. Pass an existing temporary directory, never a fixture repository. */
public class SemanticRoundTrip {
    static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    static StoredDocument source(String name,byte[] bytes)throws Exception{return new StoredDocument(UUID.randomUUID(),name,SourceType.SYNTHETIC,bytes,hash(bytes),Instant.now());}
    public static void main(String[] args)throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Pass an existing temporary output directory.");
        var directory=Path.of(args[0]);if(!Files.isDirectory(directory))throw new IllegalArgumentException("Output directory must exist.");
        var observations=new PdfObservationService();var semantic=new PdfSemanticService(observations);var proposals=new TagProposalService();var writer=new PdfTransactionService(observations,semantic,proposals);
        var mapper=new ObjectMapper().findAndRegisterModules();
        var tagged=source("semantic-source.pdf",PortfolioSamples.semantic());var snapshot=semantic.inspect(tagged.originalBytes());
        var figure=snapshot.nodes().stream().filter(PdfSemanticService.Node::altEditable).findFirst().orElseThrow();
        var parent=snapshot.nodes().stream().filter(PdfSemanticService.Node::reorderable).findFirst().orElseThrow();var order=new ArrayList<>(parent.childIds());Collections.swap(order,0,1);
        var path=snapshot.regions().stream().filter(m->m.region().kind().equals("PATH")).findFirst().orElseThrow();
        var result=writer.export(tagged,new PdfTransactionService.Request(tagged.sha256(),PdfSemanticService.VERSION,List.of(
            new PdfTransactionService.Operation("ALT_TEXT",figure.id(),1,"Participation increased from 30 to 48 to 70 people.",null,true,null),
            new PdfTransactionService.Operation("READING_ORDER",parent.id(),1,null,order,true,null),
            new PdfTransactionService.Operation("ARTIFACT",path.region().id(),1,null,null,true,null),
            new PdfTransactionService.Operation("LANGUAGE","document",0,"en-US",null,true,null),
            new PdfTransactionService.Operation("DISPLAY_TITLE","document",0,null,null,true,null))));
        Files.write(directory.resolve("semantic-source.pdf"),tagged.originalBytes());Files.write(directory.resolve("semantic-repaired.pdf"),result.pdf());
        mapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("semantic-record.json").toFile(),result.evidence());
        var untagged=source("untagged-source.pdf",PortfolioSamples.untagged(0).pdf());
        var tags=proposals.propose(semantic.inspect(untagged.originalBytes())).stream().map(p->new PdfTransactionService.Operation("TAG_TEXT",p.id(),p.pageNumber(),p.role(),null,true,"auto")).toList();
        var taggedResult=writer.export(untagged,new PdfTransactionService.Request(untagged.sha256(),PdfSemanticService.VERSION,tags));
        Files.write(directory.resolve("untagged-source.pdf"),untagged.originalBytes());Files.write(directory.resolve("new-tags.pdf"),taggedResult.pdf());
        mapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("tagging-record.json").toFile(),taggedResult.evidence());
        var benchmark=new PortfolioBenchmarkService(semantic,proposals,writer).run(null);mapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("benchmark.json").toFile(),benchmark);
        System.out.println("Combined repair: "+result.evidence().checks()+"; source "+tagged.sha256()+"; output "+hash(result.pdf()));
        System.out.println("New tags: "+taggedResult.evidence().checks()+"; source "+untagged.sha256()+"; output "+hash(taggedResult.pdf()));
        System.out.println("Benchmark: "+benchmark.metrics()+"; baseline "+benchmark.paragraphBaseline());
    }
}
