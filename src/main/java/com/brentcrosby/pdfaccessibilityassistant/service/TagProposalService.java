package com.brentcrosby.pdfaccessibilityassistant.service;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class TagProposalService {
    public static final String VERSION="layout-rules-v1";
    public record Proposal(String id,int pageNumber,List<String> regionIds,List<Integer> operators,String text,
                           PdfObservationService.Bounds bounds,String role,double heuristicScore,List<String> reasons) {}
    public List<Proposal> propose(PdfSemanticService.Snapshot snapshot) {
        var result=new ArrayList<Proposal>();
        for(var page:snapshot.pages()) {
            var candidates=snapshot.regions().stream().filter(m->m.region().pageNumber()==page.pageNumber() && m.status().equals("UNTAGGED")
                    && m.region().kind().equals("TEXT") && m.region().unmappedGlyphs()==0 && !m.region().text().isBlank()).map(PdfSemanticService.Membership::region).toList();
            Map<Integer,List<PdfObservationService.Region>> operators=new LinkedHashMap<>();
            candidates.forEach(r->operators.computeIfAbsent(r.sourceOperatorIndex(),k->new ArrayList<>()).add(r));
            var groups=new ArrayList<>(operators.values());
            groups.sort(Comparator.comparingDouble((List<PdfObservationService.Region> g)->bounds(g).y()).thenComparingDouble(g->bounds(g).x()));
            double[] heights=groups.stream().mapToDouble(g->bounds(g).height()).sorted().toArray();
            double median=heights.length==0?1:heights[(heights.length-1)/2];
            // Merge nearby aligned body lines only; columns and semantic-marker lines remain separate.
            for(int i=0;i+1<groups.size();) {
                var a=groups.get(i);var b=groups.get(i+1);var ba=bounds(a);var bb=bounds(b);
                if(role(a,median).equals("P") && role(b,median).equals("P") && Math.abs(ba.x()-bb.x())<.015
                        && bb.y()-ba.y()-ba.height()>=-.002 && bb.y()-ba.y()-ba.height()<median*.8
                        && bb.height()<median*1.2 && a.size()<8) { a.addAll(b);groups.remove(i+1); } else i++;
            }
            for(var group:groups) {
                String role=role(group,median),text=text(group);var reasons=new ArrayList<String>(); double score=.60;
                if(role.startsWith("H")) {reasons.add("Text height is larger than the page's median line height.");score=.82;}
                else if(role.equals("LI")) {reasons.add("Text starts with a list marker.");score=.80;}
                else if(role.equals("Caption")) {reasons.add("Text starts with a numbered figure or table label.");score=.78;}
                else reasons.add("Body-sized text without a heading, list or caption signal.");
                if(group.stream().map(PdfObservationService.Region::sourceOperatorIndex).distinct().count()>1) reasons.add("Nearby body lines share a left edge and were grouped.");
                reasons.add("Layout rules cannot determine meaning or guarantee reading order; review the proposed role.");
                result.add(new Proposal("tag-"+group.getFirst().id(),page.pageNumber(),group.stream().map(PdfObservationService.Region::id).toList(),
                        group.stream().map(PdfObservationService.Region::sourceOperatorIndex).distinct().toList(),text,bounds(group),role,score,reasons));
            }
        }
        return List.copyOf(result);
    }
    private String role(List<PdfObservationService.Region> group,double median) {
        String text=text(group);
        if(text.matches("(?is)^(figure|fig\\.?|table)\\s+\\d.*")) return "Caption";
        if(text.matches("(?s)^(?:[-•]|\\d+[.)])\\s+.*")) return "LI";
        double height=group.stream().mapToDouble(r->r.bounds().height()).max().orElse(0);
        if(height>=median*1.6 && text.length()<180) return "H1";
        if(height>=median*1.25 && text.length()<180) return "H2";
        return "P";
    }
    private static String text(List<PdfObservationService.Region> group) {return String.join(" ",group.stream().map(PdfObservationService.Region::text).toList());}
    private static PdfObservationService.Bounds bounds(List<PdfObservationService.Region> group) {
        double x=1,y=1,r=0,b=0;
        for(var region:group) {var box=region.bounds();x=Math.min(x,box.x());y=Math.min(y,box.y());r=Math.max(r,box.x()+box.width());b=Math.max(b,box.y()+box.height());}
        return new PdfObservationService.Bounds(x,y,r-x,b-y);
    }
}
