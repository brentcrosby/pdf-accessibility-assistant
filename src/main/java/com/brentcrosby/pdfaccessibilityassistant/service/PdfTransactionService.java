package com.brentcrosby.pdfaccessibilityassistant.service;

import com.brentcrosby.pdfaccessibilityassistant.domain.StoredDocument;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import java.io.*;
import java.time.Instant;
import java.util.*;

/** All changes are built in one private document and released only after verification. */
@Service
public class PdfTransactionService {
    public record Operation(@NotBlank String kind,@Size(max=100) String targetId,@Min(0) int pageNumber,
                            @Size(max=2000) String value,@Size(max=1000) List<String> order,boolean confirmed,@Size(max=100) String parentId) {}
    public record Request(@NotBlank String originalSha256,@NotBlank String semanticVersion,
                          @NotEmpty @Size(max=25) List<@NotNull @Valid Operation> operations) {}
    public record Applied(String kind,String targetId,Object before,Object after) {}
    public record Preview(int pageNumber,byte[] before,byte[] after) {}
    public record Evidence(String schemaVersion,String originalFilename,String originalSha256,String outputSha256,
                           String outputFilename,Instant exportedAt,List<Applied> appliedActions,Map<String,Boolean> checks,
                           int pagesVerified,String expectedGraphDigest,String limitation) {}
    public record Result(byte[] pdf,Evidence evidence,List<Preview> previews) {}
    private record Edit(int first,int last,List<Object> prefix,List<Object> suffix) {}
    private final PdfObservationService observations;
    private final PdfSemanticService semantics;
    private final TagProposalService proposals;
    public PdfTransactionService(PdfObservationService observations,PdfSemanticService semantics,TagProposalService proposals) {
        this.observations=observations;this.semantics=semantics;this.proposals=proposals;
    }

    public Result export(StoredDocument source,Request request) {
        require(source.sha256().equals(request.originalSha256()) && PdfSemanticService.VERSION.equals(request.semanticVersion()),"This repair queue belongs to a different source or semantic version. Reload the document.");
        require(request.operations()!=null && !request.operations().isEmpty() && request.operations().size()<=25,"Queue between 1 and 25 repairs.");
        byte[] original=source.originalBytes(),result;String expectedGraph;
        List<Applied> applied=new ArrayList<>();Set<Integer> affected=new TreeSet<>();
        List<TagProposalService.Proposal> acceptedTags=new ArrayList<>();
        try(var doc=Loader.loadPDF(original);var output=new ByteArrayOutputStream()) {
            require(!doc.isEncrypted() && doc.getSignatureDictionaries().isEmpty() && !doc.getDocumentCatalog().getCOSObject().containsKey(COSName.PERMS),"Encrypted, signed or certified documents are not supported for repair transactions.");
            var index=new PdfStructureIndex(doc);var snapshot=semantics.inspect(original,doc,index);
            require(snapshot.pages().stream().noneMatch(PdfObservationService.PageObservations::truncated),"Incomplete page observations prevent transaction verification.");
            Map<String,PdfSemanticService.Node> nodes=new HashMap<>();snapshot.nodes().forEach(n->nodes.put(n.id(),n));
            Map<String,TagProposalService.Proposal> proposed=new HashMap<>();proposals.propose(snapshot).forEach(p->proposed.put(p.id(),p));
            Map<Integer,PathRepairProgram> programs=new HashMap<>();Map<Integer,List<Edit>> edits=new HashMap<>();
            Set<String> seen=new HashSet<>(), reordered=new HashSet<>(),tagParents=new HashSet<>();
            for(var op:request.operations()) {
                require(op!=null && op.kind()!=null,"A repair action is required.");
                require(op.confirmed(),"Each queued repair must be explicitly accepted.");
                String target=Set.of("LANGUAGE","DISPLAY_TITLE").contains(op.kind())?"document":op.targetId();
                require(seen.add(op.kind()+":"+target),"Duplicate repair targets are not allowed.");
                if(op.kind().equals("READING_ORDER")) reordered.add(op.targetId());
                if(op.kind().equals("TAG_TEXT")) tagParents.add(op.parentId());
            }
            require(Collections.disjoint(reordered,tagParents),"New tags and reading-order changes for the same parent need separate exports.");
            if(request.operations().stream().anyMatch(op->Set.of("ALT_TEXT","READING_ORDER","TAG_TEXT").contains(op.kind()))) {
                require(index.issues.isEmpty() && snapshot.nodes().stream().allMatch(n->n.issues().isEmpty()),"Existing structure references have unresolved issues. Resolve those before writing semantic repairs.");
            }
            COSDictionary tagRoot=index.root,autoParent=null;
            Map<Integer,COSBase> parentEntries=new TreeMap<>(index.parentTree);
            for(var op:request.operations()) {
                switch(op.kind()) {
                    case "ALT_TEXT" -> {
                        var node=nodes.get(op.targetId());require(node!=null && node.altEditable(),"Select a mapped Figure with valid structure references.");
                        String value=nonBlank(op.value(),2000,"Alternative text");
                        index.entries.get(node.id()).dictionary.setString(COSName.ALT,value);
                        applied.add(new Applied(op.kind(),node.id(),node.altText(),value));node.locations().forEach(l->affected.add(l.pageNumber()));
                    }
                    case "READING_ORDER" -> {
                        var node=nodes.get(op.targetId());require(node!=null && node.reorderable(),"This parent does not have an editable sequence of mapped structure children.");
                        require(op.order()!=null && op.order().size()==node.childIds().size() && new HashSet<>(op.order()).size()==op.order().size()
                                && new HashSet<>(op.order()).equals(new HashSet<>(node.childIds())),"Reading order must include every existing sibling exactly once.");
                        var children=new COSArray();op.order().forEach(id->children.add(index.entries.get(id).dictionary));
                        index.entries.get(node.id()).dictionary.setItem(COSName.K,children);
                        applied.add(new Applied(op.kind(),node.id(),node.childIds(),List.copyOf(op.order())));node.locations().forEach(l->affected.add(l.pageNumber()));
                    }
                    case "TAG_TEXT" -> {
                        var proposal=proposed.get(op.targetId());require(proposal!=null,"The accepted text proposal no longer matches the source.");
                        require(op.value()!=null && Set.of("H1","H2","H3","P","LI","Caption").contains(op.value()),"Choose a supported text role.");
                        acceptedTags.add(proposal);
                        COSDictionary parent;
                        if(index.root==null) {
                            require(op.parentId()==null || op.parentId().equals("auto"),"Choose the new document structure as the parent.");
                            if(tagRoot==null) {
                                tagRoot=new COSDictionary();tagRoot.setItem(COSName.TYPE,COSName.STRUCT_TREE_ROOT);
                                doc.getDocumentCatalog().getCOSObject().setItem(COSName.STRUCT_TREE_ROOT,tagRoot);
                                autoParent=element("Document",tagRoot);append(tagRoot,autoParent);
                                var info=doc.getDocumentCatalog().getCOSObject().getCOSDictionary(COSName.MARK_INFO);
                                if(info==null) info=new COSDictionary();info.setBoolean(COSName.getPDFName("Marked"),true);doc.getDocumentCatalog().getCOSObject().setItem(COSName.MARK_INFO,info);
                            }
                            parent=autoParent;
                        } else {
                            var entry=index.entries.get(op.parentId());
                            require(entry!=null && Set.of("Document","Part","Sect","Div").contains(entry.role) && entry.childrenOnly,"Select a Document, Part, Sect or Div containing only child elements.");
                            parent=entry.dictionary;
                        }
                        int number=proposal.pageNumber();var page=doc.getPage(number-1);
                        var leaf=element(op.value(),parent);
                        if(op.value().equals("LI")) {var list=element("L",parent);append(parent,list);leaf=element("LI",list);append(list,leaf);var body=element("LBody",leaf);append(leaf,body);leaf=body;}
                        else append(parent,leaf);
                        leaf.setItem(COSName.PG,page);
                        int parentKey=page.getCOSObject().getInt(COSName.STRUCT_PARENTS,-1);
                        if(parentKey<0) {parentKey=parentEntries.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1)+1;page.getCOSObject().setInt(COSName.STRUCT_PARENTS,parentKey);}
                        COSBase existing=parentEntries.get(parentKey);require(existing==null || existing instanceof COSArray,"The page's parent-tree entry is not a content array.");
                        var parentArray=existing==null?new COSArray():(COSArray)existing;parentEntries.put(parentKey,parentArray);
                        int maxUsed=snapshot.regions().stream().filter(m->m.region().pageNumber()==number && m.region().mcid()!=null).mapToInt(m->m.region().mcid()).max().orElse(-1);
                        require(maxUsed<10000 && parentArray.size()<10000,"The page's MCID space exceeds the tagging limit.");
                        while(parentArray.size()<=maxUsed) parentArray.add(COSNull.NULL);
                        var kidRefs=new COSArray();var program=program(doc,number,programs);
                        for(int operator:proposal.operators()) {
                            int mcid=parentArray.size();parentArray.add(leaf);kidRefs.add(COSInteger.get(mcid));
                            var properties=new COSDictionary();properties.setInt(COSName.MCID,mcid);
                            int[] span=textSpan(program,operator);
                            addEdit(edits,number,new Edit(span[0],span[1],List.of(COSName.getPDFName(op.value().equals("LI")?"LBody":op.value()),properties,Operator.getOperator("BDC")),List.of(Operator.getOperator("EMC"))));
                        }
                        leaf.setItem(COSName.K,kidRefs);
                        applied.add(new Applied(op.kind(),proposal.id(),"UNTAGGED",Map.of("role",op.value(),"pageNumber",number,"regionIds",proposal.regionIds())));affected.add(number);
                    }
                    case "ARTIFACT" -> {
                        var member=snapshot.regions().stream().filter(m->m.region().id().equals(op.targetId()) && m.region().pageNumber()==op.pageNumber()).findFirst().orElse(null);
                        require(member!=null && member.status().equals("UNTAGGED") && member.region().kind().equals("PATH"),"Select an untagged direct-page decorative path.");
                        var program=program(doc,op.pageNumber(),programs);var span=program.target(member.region().sourceOperatorIndex());require(span.eligible(),span.refusal());
                        addEdit(edits,op.pageNumber(),new Edit(span.firstToken(),span.lastToken(),List.of(COSName.ARTIFACT,Operator.getOperator("BMC")),List.of(Operator.getOperator("EMC"))));
                        applied.add(new Applied(op.kind(),op.targetId(),"UNTAGGED","ARTIFACT"));affected.add(op.pageNumber());
                    }
                    case "LANGUAGE" -> {
                        String value=nonBlank(op.value(),100,"Document language");String normalized=Locale.forLanguageTag(value).toLanguageTag();
                        require(!normalized.equals("und") && normalized.equalsIgnoreCase(value),"Use a valid BCP 47 language, such as en-US.");
                        applied.add(new Applied(op.kind(),"document",doc.getDocumentCatalog().getLanguage(),value));doc.getDocumentCatalog().setLanguage(value);
                    }
                    case "DISPLAY_TITLE" -> {
                        require(doc.getDocumentInformation().getTitle()!=null && !doc.getDocumentInformation().getTitle().isBlank(),"A title must already exist to enable its display.");
                        var catalog=doc.getDocumentCatalog().getCOSObject();var prefs=catalog.getCOSDictionary(COSName.VIEWER_PREFERENCES);if(prefs==null)prefs=new COSDictionary();
                        applied.add(new Applied(op.kind(),"document",prefs.getBoolean(COSName.DISPLAY_DOC_TITLE,false),true));prefs.setBoolean(COSName.DISPLAY_DOC_TITLE,true);catalog.setItem(COSName.VIEWER_PREFERENCES,prefs);
                    }
                    default -> throw new PdfInputException("Unsupported repair action: "+op.kind());
                }
            }
            if(request.operations().stream().anyMatch(op->op.kind().equals("TAG_TEXT"))) {
                var tree=new COSDictionary();var nums=new COSArray();parentEntries.forEach((key,value)->{nums.add(COSInteger.get(key));nums.add(value);});tree.setItem(COSName.NUMS,nums);
                tagRoot.setItem(COSName.PARENT_TREE,tree);tagRoot.setInt(COSName.PARENT_TREE_NEXT_KEY,parentEntries.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1)+1);
            }
            for(var entry:edits.entrySet()) {
                var tokens=new ArrayList<>(programs.get(entry.getKey()).tokens);var descending=new ArrayList<>(entry.getValue());descending.sort(Comparator.comparingInt(Edit::first).reversed());
                for(var edit:descending) {tokens.addAll(edit.last()+1,edit.suffix());tokens.addAll(edit.first(),edit.prefix());}
                doc.getPage(entry.getKey()-1).setContents(new PDStream(doc,new ByteArrayInputStream(PathRepairProgram.canonical(tokens)),COSName.FLATE_DECODE));
            }
            expectedGraph=PdfPreservationDigest.of(doc,0);doc.save(output);result=output.toByteArray();
            require(result.length<=10*1024*1024,"The repaired copy exceeds the 10 MB intake limit.");
        } catch(IOException ex) {throw new PdfInputException("The transaction could not be written. No partial copy was returned.",ex);}
        var previews=new ArrayList<Preview>();int pagesVerified;
        try(var before=Loader.loadPDF(original);var after=Loader.loadPDF(result)) {
            require(expectedGraph.equals(PdfPreservationDigest.of(after,0)),"The reopened structure does not match the approved transaction. No copy was returned.");
            require(before.getNumberOfPages()==after.getNumberOfPages(),"Page count changed unexpectedly.");pagesVerified=before.getNumberOfPages();
            for(int p=1;p<=pagesVerified;p++) {
                var stripper=new PDFTextStripper();stripper.setStartPage(p);stripper.setEndPage(p);
                require(stripper.getText(before).equals(stripper.getText(after)),"Extracted text changed on page "+p+". No copy was returned.");
                byte[] a=observations.render(original,p),b=observations.render(result,p);
                require(PdfArtifactRepairService.samePixels(a,b),"Appearance changed on page "+p+". No copy was returned.");
                if(affected.contains(p) || p==1 && affected.isEmpty()) previews.add(new Preview(p,a,b));
            }
            if(request.operations().stream().anyMatch(op->op.kind().equals("TAG_TEXT"))) {
                var inspected=semantics.inspect(result,after,new PdfStructureIndex(after));
                for(var proposal:acceptedTags) {
                    var mapped=inspected.regions().stream().filter(m->proposal.regionIds().contains(m.region().id())).toList();
                    require(mapped.size()==proposal.regionIds().size() && mapped.stream().allMatch(m->m.status().equals("TAGGED")),"A written text group did not resolve through its parent tree.");
                }
            }
        } catch(IOException ex) {throw new PdfInputException("The repaired document could not be verified. No copy was returned.",ex);}
        require(source.sha256().equals(PdfArtifactRepairService.sha256(source.originalBytes())),"Original byte verification failed.");
        String filename=source.originalFilename().replaceFirst("(?i)\\.pdf$","")+"-repaired.pdf";
        return new Result(result,new Evidence("1.0",source.originalFilename(),source.sha256(),PdfArtifactRepairService.sha256(result),filename,Instant.now(),List.copyOf(applied),
                Map.of("expectedDocumentGraph",true,"allPageTextUnchanged",true,"allPagePreviewPixelsUnchanged",true,"originalBytesUnchanged",true),pagesVerified,expectedGraph,
                "Only listed actions were applied. Pixel checks use bounded preview resolution. Semantic correctness depends on human review; tagging a subset does not make the document fully accessible."),previews);
    }
    private static void require(boolean condition,String message) {if(!condition)throw new PdfInputException(message);}
    private static String nonBlank(String value,int maximum,String name) {require(value!=null && !value.isBlank() && value.length()<=maximum,name+" must contain 1 to "+maximum+" characters.");return value.strip();}
    private static COSDictionary element(String role,COSDictionary parent) {var d=new COSDictionary();d.setItem(COSName.TYPE,COSName.STRUCT_ELEM);d.setName(COSName.S,role);d.setItem(COSName.P,parent);return d;}
    private static void append(COSDictionary parent,COSDictionary child) {COSBase existing=parent.getDictionaryObject(COSName.K);COSArray array;if(existing instanceof COSArray a)array=a;else{array=new COSArray();if(existing!=null)array.add(existing);parent.setItem(COSName.K,array);}array.add(child);}
    private static PathRepairProgram program(PDDocument doc,int number,Map<Integer,PathRepairProgram> programs)throws IOException {if(!programs.containsKey(number))programs.put(number,new PathRepairProgram(doc.getPage(number-1)));return programs.get(number);}
    private static int[] textSpan(PathRepairProgram program,int ordinal) {
        int index=-1,start=0;
        for(int i=0;i<program.tokens.size();i++) if(program.tokens.get(i) instanceof Operator operator) {
            if(++index==ordinal) {require(Set.of("Tj","TJ","'","\"").contains(operator.getName()),"The proposed group does not map to a supported text drawing instruction.");return new int[]{start,i};}start=i+1;
        }
        throw new PdfInputException("Text drawing instruction is missing.");
    }
    private static void addEdit(Map<Integer,List<Edit>> edits,int page,Edit edit) {
        var list=edits.computeIfAbsent(page,k->new ArrayList<>());require(list.stream().noneMatch(e->e.first()<=edit.last() && edit.first()<=e.last()),"Queued content repairs overlap. Remove one of the conflicting actions.");list.add(edit);
    }
}
