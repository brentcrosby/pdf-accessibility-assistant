package com.brentcrosby.pdfaccessibilityassistant.service;

import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import java.util.*;

/** Bounded structure and parent-tree index. Only direct-page MCRs are resolved for writing. */
final class PdfStructureIndex {
    record Ref(int page, int mcid) {}
    static final class Entry {
        final String id, parent, role, originalRole;
        final COSDictionary dictionary;
        final List<String> children = new ArrayList<>(), issues = new ArrayList<>();
        final List<Ref> references = new ArrayList<>();
        boolean childrenOnly = true;
        Entry(String id,String parent,String role,String originalRole,COSDictionary dictionary) {
            this.id=id; this.parent=parent; this.role=role; this.originalRole=originalRole; this.dictionary=dictionary;
        }
    }
    final Map<String,Entry> entries = new LinkedHashMap<>();
    final Map<Ref,List<Entry>> owners = new HashMap<>();
    final Map<Integer,COSBase> parentTree = new TreeMap<>();
    final List<String> issues = new ArrayList<>();
    final COSDictionary root;
    private final IdentityHashMap<COSDictionary,Integer> pages = new IdentityHashMap<>();
    private final Set<COSBase> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    private final PDDocument document;
    private int visits;

    PdfStructureIndex(PDDocument document) {
        this.document=document;
        for(int i=0;i<document.getNumberOfPages();i++) pages.put(document.getPage(i).getCOSObject(),i+1);
        root=document.getDocumentCatalog().getCOSObject().getCOSDictionary(COSName.STRUCT_TREE_ROOT);
        if(root == null) return;
        var entry=new Entry("root",null,"StructTreeRoot","StructTreeRoot",root); entries.put("root",entry); seen.add(root);
        readKids(root.getDictionaryObject(COSName.K),entry,null,0);
        readNumberTree(root.getCOSDictionary(COSName.PARENT_TREE), Collections.newSetFromMap(new IdentityHashMap<>()),0);
    }
    private void budget(int depth) { if(++visits>10_000 || depth>60) throw new PdfInputException("The structure tree exceeds the semantic review limits."); }
    private void readKids(COSBase kid, Entry parent, COSDictionary inheritedPage, int depth) {
        budget(depth);
        if(kid==null || kid instanceof COSNull) return;
        if(kid instanceof COSArray array) { if(!seen.add(array)) {parent.issues.add("Repeated or cyclic children array");return;} for(COSBase child:array) readKids(resolve(child),parent,inheritedPage,depth+1); return; }
        if(kid instanceof COSInteger n) { parent.childrenOnly=false; addRef(parent,inheritedPage,n.intValue()); return; }
        if(!(kid instanceof COSDictionary dict)) {parent.issues.add("Unsupported structure child");parent.childrenOnly=false;return;}
        String type=dict.getNameAsString(COSName.TYPE);
        COSDictionary page=dict.getCOSDictionary(COSName.PG); if(page==null) page=inheritedPage;
        if("MCR".equals(type)) {
            parent.childrenOnly=false;
            if(dict.containsKey(COSName.getPDFName("Stm"))) parent.issues.add("Form-stream marked content is not mapped yet");
            else if(dict.getDictionaryObject(COSName.MCID) instanceof COSInteger n) addRef(parent,page,n.intValue());
            else parent.issues.add("Invalid marked-content reference");
        } else if("StructElem".equals(type) || dict.containsKey(COSName.S)) {
            if(!seen.add(dict)) { parent.issues.add("Repeated or cyclic structure element"); return; }
            String original=dict.getNameAsString(COSName.S), role=role(original);
            var child=new Entry("s"+entries.size(),parent.id,role,original,dict); entries.put(child.id,child); parent.children.add(child.id);
            if(dict.getDictionaryObject(COSName.P)!=parent.dictionary) child.issues.add("Structure parent link does not match");
            if(dict.containsKey(COSName.getPDFName("NS"))) child.issues.add("PDF 2 namespace roles are not supported for repair");
            if("UNKNOWN".equals(role)) child.issues.add("Unknown or cyclic role mapping");
            readKids(dict.getDictionaryObject(COSName.K),child,page,depth+1);
        } else { parent.childrenOnly=false; parent.issues.add("Object or unsupported structure reference is not mapped"); }
    }
    private void addRef(Entry owner,COSDictionary page,int mcid) {
        Integer number=pages.get(page);
        if(number==null || mcid<0) {owner.issues.add("Missing page or invalid marked-content ID");return;}
        var ref=new Ref(number,mcid); owner.references.add(ref); owners.computeIfAbsent(ref,k->new ArrayList<>()).add(owner);
    }
    private void readNumberTree(COSDictionary node,Set<COSBase> visited,int depth) {
        budget(depth); if(node==null) return;
        if(!visited.add(node)) {issues.add("Cyclic parent tree");return;}
        COSArray nums=node.getCOSArray(COSName.NUMS), kids=node.getCOSArray(COSName.KIDS);
        if(nums!=null) {
            if(nums.size()%2!=0) issues.add("Odd parent-tree number array");
            for(int i=0;i+1<nums.size();i+=2) {
                if(!(nums.getObject(i) instanceof COSInteger key) || key.intValue()<0) {issues.add("Invalid parent-tree key");continue;}
                if(parentTree.putIfAbsent(key.intValue(),nums.getObject(i+1))!=null) issues.add("Duplicate parent-tree key");
            }
        }
        if(kids!=null) for(COSBase kid:kids) {
            if(resolve(kid) instanceof COSDictionary child) readNumberTree(child,visited,depth+1); else issues.add("Invalid parent-tree child");
        }
    }
    boolean parentAgrees(Ref ref,Entry owner) {
        if(!issues.isEmpty()) return false;
        COSBase key=document.getPage(ref.page()-1).getCOSObject().getDictionaryObject(COSName.STRUCT_PARENTS);
        return key instanceof COSInteger number && parentTree.get(number.intValue()) instanceof COSArray array
                && ref.mcid()<array.size() && array.getObject(ref.mcid())==owner.dictionary;
    }
    String role(String original) {
        Set<String> standard=Set.of("Document","Part","Sect","Div","P","H","H1","H2","H3","H4","H5","H6","L","LI","Lbl","LBody","Table","TR","TH","TD","THead","TBody","TFoot","Figure","Caption","Span","Link","Annot","Form","Quote","Note","Reference","BibEntry","Code","TOC","TOCI","NonStruct","Private","Formula","Ruby","Warichu");
        var mappings=root==null?null:root.getCOSDictionary(COSName.ROLE_MAP); Set<String> seenRoles=new HashSet<>();
        String role=original;
        while(role!=null && !standard.contains(role) && seenRoles.add(role) && seenRoles.size()<20) role=mappings==null?null:mappings.getNameAsString(COSName.getPDFName(role));
        return role!=null && standard.contains(role)?role:"UNKNOWN";
    }
    List<Entry> subtree(String id) { var result=new ArrayList<Entry>(); collect(entries.get(id),result);return result; }
    private void collect(Entry entry,List<Entry> out) {if(entry==null)return;out.add(entry);for(String id:entry.children)collect(entries.get(id),out);}
    static COSBase resolve(COSBase value) {return value instanceof COSObject object?object.getObject():value;}
}
