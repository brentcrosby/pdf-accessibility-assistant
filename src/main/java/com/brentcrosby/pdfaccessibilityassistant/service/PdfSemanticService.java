package com.brentcrosby.pdfaccessibilityassistant.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.*;

@Service
public class PdfSemanticService {
    public static final String VERSION="semantics-v1";
    public record Location(int pageNumber,String regionId) {}
    public record Membership(PdfObservationService.Region region,String status,String nodeId,String role,String reason) {}
    public record Node(String id,String parentId,String role,String originalRole,String altText,List<String> childIds,
                       List<Location> locations,boolean altEditable,boolean reorderable,boolean appendEligible,List<String> issues) {}
    public record Snapshot(String version,List<Node> nodes,List<Membership> regions,List<PdfObservationService.PageObservations> pages,List<String> warnings) {}
    private final PdfObservationService observations;
    public PdfSemanticService(PdfObservationService observations) {this.observations=observations;}
    public Snapshot inspect(byte[] bytes) {
        try(var doc=Loader.loadPDF(bytes)) {return inspect(bytes,doc,new PdfStructureIndex(doc));}
        catch(IOException ex) {throw new PdfInputException("The PDF structure could not be inspected.",ex);}
    }
    Snapshot inspect(byte[] bytes,PDDocument doc,PdfStructureIndex index) {
        if(doc.getNumberOfPages()>20) throw new PdfInputException("Semantic review currently supports documents up to 20 pages.");
        var pages=new ArrayList<PdfObservationService.PageObservations>();var members=new ArrayList<Membership>();var warnings=new ArrayList<>(index.issues);
        for(int number=1;number<=doc.getNumberOfPages();number++) {
            var page=observations.inspect(bytes,number); pages.add(page);
            boolean partial=page.truncated() || page.warnings().stream().anyMatch(w->w.startsWith("Malformed marked content"));
            if(partial) warnings.add("Page "+number+" has incomplete or malformed content; mapping is unresolved.");
            Map<Integer,Set<Integer>> sequences=new HashMap<>();
            for(var region:page.regions()) if(region.mcid()!=null && region.sourceOperatorIndex()>=0) sequences.computeIfAbsent(region.mcid(),k->new HashSet<>()).add(region.markedSequence());
            for(var region:page.regions()) {
                String status="UNRESOLVED",node=null,role=null,reason="Content inside reusable forms is not mapped to a page tag.";
                if(partial) reason="Page coverage or marked-content syntax is incomplete.";
                else if(region.artifact()) { status=region.mcid()==null?"ARTIFACT":"UNRESOLVED";reason=region.mcid()==null?"Inside Artifact marked content.":"Conflicting artifact and MCID scopes."; }
                else if(region.sourceOperatorIndex()>=0) {
                    if(region.mcid()==null) {status=region.markedContentPresent()?"UNRESOLVED":"UNTAGGED";reason=region.markedContentPresent()?"Marked content without a resolvable MCID.":"Direct page content outside marked-content scopes.";}
                    else {
                        var ref=new PdfStructureIndex.Ref(number,region.mcid());var owners=index.owners.getOrDefault(ref,List.of());
                        if(sequences.get(region.mcid()).size()!=1) reason="The MCID is reused in separate content sequences.";
                        else if(owners.size()!=1) reason="No unique structure element owns this page and MCID.";
                        else if(!index.parentAgrees(ref,owners.getFirst())) reason="The parent-tree entry does not agree with the structure tree.";
                        else if(!owners.getFirst().issues.isEmpty()) reason="The owning structure element has unresolved issues.";
                        else {status="TAGGED";node=owners.getFirst().id;role=owners.getFirst().role;reason="Page MCID, structure element and parent tree agree.";}
                    }
                }
                members.add(new Membership(region,status,node,role,reason));
            }
        }
        var nodes=new ArrayList<Node>();
        for(var entry:index.entries.values()) {
            var descendants=index.subtree(entry.id); Set<String> ids=new HashSet<>();descendants.forEach(e->ids.add(e.id));
            var locations=members.stream().filter(m->ids.contains(m.nodeId())).map(m->new Location(m.region().pageNumber(),m.region().id())).toList();
            var issues=new ArrayList<String>();descendants.forEach(e->issues.addAll(e.issues));
            for(var desc:descendants) for(var ref:desc.references) {
                if(members.stream().noneMatch(m->m.status().equals("TAGGED") && desc.id.equals(m.nodeId()) && m.region().pageNumber()==ref.page() && Objects.equals(m.region().mcid(),ref.mcid()))) issues.add("Unmapped content reference on page "+ref.page()+", MCID "+ref.mcid());
            }
            nodes.add(new Node(entry.id,entry.parent,entry.role,entry.originalRole,entry.dictionary.getString(COSName.ALT),List.copyOf(entry.children),locations,
                    entry.role.equals("Figure") && issues.isEmpty() && !locations.isEmpty(),entry.childrenOnly && entry.children.size()>1 && issues.isEmpty(),
                    Set.of("Document","Part","Sect","Div").contains(entry.role) && entry.childrenOnly && issues.isEmpty(),List.copyOf(new LinkedHashSet<>(issues))));
        }
        if(members.stream().anyMatch(m->m.status().equals("UNRESOLVED"))) warnings.add("Some observations have unresolved membership. They are excluded from tagging and figure repair.");
        warnings.add("Mapping covers supported visible observations. Annotation appearances, form-stream references, hidden content and PDF 2 namespaces require further support.");
        return new Snapshot(VERSION,nodes,members,pages,warnings);
    }
}
