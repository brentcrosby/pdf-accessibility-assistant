package com.brentcrosby.pdfaccessibilityassistant.service;

import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Compare the reachable catalog and Info graph, excluding ONLY the repaired page's Contents.
 * Object numbers and stream compression may change on save; logical references and decoded data may not. */
final class PdfPreservationDigest {
    private final MessageDigest digest;
    private final IdentityHashMap<COSBase,Integer> seen = new IdentityHashMap<>();
    private final COSDictionary repairedPage;
    private long decodedBytes;
    private int nodes;
    private PdfPreservationDigest(COSDictionary repairedPage) {
        this.repairedPage = repairedPage;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    static String of(PDDocument doc, int pageNumber) throws IOException {
        var state = new PdfPreservationDigest(pageNumber == 0 ? null : doc.getPage(pageNumber-1).getCOSObject());
        state.visit(doc.getDocumentCatalog().getCOSObject(),0);
        state.visit(doc.getDocument().getTrailer().getDictionaryObject(COSName.INFO),0);
        return HexFormat.of().formatHex(state.digest.digest());
    }
    private void value(String value) { byte[] bytes = value.getBytes(StandardCharsets.UTF_8); digest.update((bytes.length + ":").getBytes(StandardCharsets.US_ASCII)); digest.update(bytes); }
    private void visit(COSBase item, int depth) throws IOException {
        if (++nodes > 100_000 || depth > 100) throw new PdfInputException("Document structure exceeds the artifact verification limit.");
        if (item instanceof COSObject object) { visit(object.getObject(),depth); return; }
        if (item == null || item instanceof COSNull) { value("null"); return; }
        if (item instanceof COSDictionary || item instanceof COSArray) {
            Integer previous = seen.get(item);
            if (previous != null) { value("ref:" + previous); return; }
            seen.put(item,seen.size());
        }
        if (item instanceof COSDictionary dictionary) {
            value(item instanceof COSStream ? "stream" : "dictionary");
            for (COSName key : dictionary.keySet().stream().sorted(Comparator.comparing(COSName::getName)).toList()) {
                if (dictionary == repairedPage && key.equals(COSName.CONTENTS)) continue;
                if (item instanceof COSStream && Set.of(COSName.LENGTH,COSName.FILTER,COSName.DECODE_PARMS).contains(key)) continue;
                value(key.getName()); visit(dictionary.getDictionaryObject(key),depth+1);
            }
            value("end-dictionary");
            if (item instanceof COSStream stream) {
                MessageDigest streamDigest;
                try { streamDigest = MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
                try (var input = stream.createInputStream()) {
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = input.read(buffer)) != -1) {
                        decodedBytes += count;
                        if (decodedBytes > 32L * 1024 * 1024) throw new PdfInputException("Decoded document data exceeds the 32 MB artifact verification limit.");
                        streamDigest.update(buffer,0,count);
                    }
                }
                value(HexFormat.of().formatHex(streamDigest.digest()));
            }
        } else if (item instanceof COSArray array) {
            value("array:" + array.size()); for (COSBase child : array) visit(child,depth+1); value("end-array");
        } else if (item instanceof COSString string) value("string:" + HexFormat.of().formatHex(string.getBytes()));
        else value(item.toString());
    }
}
