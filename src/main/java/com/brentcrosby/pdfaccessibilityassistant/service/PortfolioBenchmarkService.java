package com.brentcrosby.pdfaccessibilityassistant.service;

import com.brentcrosby.pdfaccessibilityassistant.domain.*;
import org.springframework.stereotype.Service;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

@Service
public class PortfolioBenchmarkService {
    public static final String CORPUS="synthetic-layout-v1";
    public record Row(String id,String document,String text,String expected,String predicted,List<String> reasons) {}
    public record RoleMetric(String role,int truePositive,int falsePositive,int falseNegative,double precision,double recall) {}
    public record Metrics(int total,int predicted,int correct,int abstained,double precision,double recall,double coverage,int falseArtifactPredictions,List<RoleMetric> byRole) {}
    public record Comparison(@NotBlank @Size(max=100) String label,@NotBlank String corpusVersion,@NotNull Map<String,String> sourceHashes,@NotNull @Size(max=100) Map<String,String> predictions) {}
    public record Report(String corpusVersion,String algorithm,Map<String,String> sourceHashes,List<Row> rows,Metrics metrics,Metrics paragraphBaseline,
                         String comparisonLabel,Metrics comparison,Map<String,Boolean> repairChecks,Comparison comparisonTemplate,String limitation) {}
    private final PdfSemanticService semantics;private final TagProposalService proposals;private final PdfTransactionService transactions;
    public PortfolioBenchmarkService(PdfSemanticService semantics,TagProposalService proposals,PdfTransactionService transactions){this.semantics=semantics;this.proposals=proposals;this.transactions=transactions;}
    public Report run(Comparison imported) {
        var hashes=new LinkedHashMap<String,String>();var rows=new ArrayList<Row>();var predictions=new LinkedHashMap<String,String>();
        for(int i=0;i<2;i++) {
            var sample=PortfolioSamples.untagged(i);hashes.put(sample.id(),PdfArtifactRepairService.sha256(sample.pdf()));
            var proposed=proposals.propose(semantics.inspect(sample.pdf()));
            for(int j=0;j<sample.labels().size();j++) {
                var label=sample.labels().get(j);var matches=proposed.stream().filter(p->p.text().equals(label.text())).toList();
                String id=sample.id()+":"+j,role=matches.size()==1?matches.getFirst().role():"ABSTAIN";
                rows.add(new Row(id,sample.id(),label.text(),label.role(),role,matches.size()==1?matches.getFirst().reasons():List.of("No uniquely aligned proposal")));
                if(!role.equals("ABSTAIN")) predictions.put(id,role);
            }
        }
        var baseline=new LinkedHashMap<String,String>();rows.forEach(r->baseline.put(r.id(),"P"));
        Metrics comparison=null;
        if(imported!=null) {
            if(!CORPUS.equals(imported.corpusVersion()) || !hashes.equals(imported.sourceHashes()))throw new PdfInputException("Comparison corpus version and source hashes must match this benchmark.");
            if(imported.predictions()==null || !baseline.keySet().containsAll(imported.predictions().keySet()) || imported.predictions().values().stream().anyMatch(v->v==null || !Set.of("H1","H2","H3","P","LI","Caption","ARTIFACT","ABSTAIN").contains(v)))throw new PdfInputException("Comparison rows or predicted roles are invalid.");
            comparison=score(rows,imported.predictions());
        }
        byte[] sample=PortfolioSamples.semantic();var snapshot=semantics.inspect(sample);var figure=snapshot.nodes().stream().filter(PdfSemanticService.Node::altEditable).findFirst().orElseThrow();
        var source=new StoredDocument(UUID.randomUUID(),"benchmark.pdf",SourceType.SYNTHETIC,sample,PdfArtifactRepairService.sha256(sample),Instant.now());
        var repaired=transactions.export(source,new PdfTransactionService.Request(source.sha256(),PdfSemanticService.VERSION,List.of(
                new PdfTransactionService.Operation("ALT_TEXT",figure.id(),0,"Participation increased from 30 to 48 to 70 people.",null,true,null),
                new PdfTransactionService.Operation("LANGUAGE","document",0,"en-US",null,true,null))));
        return new Report(CORPUS,TagProposalService.VERSION,hashes,rows,score(rows,predictions),score(rows,baseline),imported==null?null:imported.label(),comparison,repaired.evidence().checks(),
                new Comparison("Replace with the evaluated tool name",CORPUS,hashes,predictions),
                "A 2-document, 14-label synthetic regression corpus, not a representative accuracy estimate. Confidence is heuristic. Comparison imports require manually aligned predictions for these exact files; no Acrobat score is supplied. Preservation checks cover one combined synthetic repair.");
    }
    static Metrics score(List<Row> rows,Map<String,String> predictions) {
        int predicted=0,correct=0,artifacts=0;var roles=new TreeSet<String>();rows.forEach(r->roles.add(r.expected()));
        var perRole=new ArrayList<RoleMetric>();
        for(var row:rows) {
            String p=predictions.get(row.id());if(p!=null && !p.equals("ABSTAIN"))predicted++;
            if(row.expected().equals(p))correct++;if("ARTIFACT".equals(p) && !row.expected().equals(p))artifacts++;
        }
        roles.addAll(predictions.values());roles.remove("ABSTAIN");
        for(String role:roles) {
            int tp=0,fp=0,fn=0;
            for(var row:rows) {boolean expected=role.equals(row.expected()),p=role.equals(predictions.get(row.id()));if(expected&&p)tp++;else if(p)fp++;else if(expected)fn++;}
            perRole.add(new RoleMetric(role,tp,fp,fn,ratio(tp,tp+fp),ratio(tp,tp+fn)));
        }
        return new Metrics(rows.size(),predicted,correct,rows.size()-predicted,ratio(correct,predicted),ratio(correct,rows.size()),ratio(predicted,rows.size()),artifacts,perRole);
    }
    private static double ratio(int a,int b){return b==0?0:(double)a/b;}
}
