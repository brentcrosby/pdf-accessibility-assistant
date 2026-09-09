package com.brentcrosby.pdfaccessibilityassistant.service;

import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDPage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

/** A deliberately small writable subset: a contiguous, unmarked path in the page stream. */
final class PathRepairProgram {
    private static final Map<String, Integer> PATH = Map.of("m",2,"l",2,"c",6,"v",4,"y",4,"h",0,"re",4);
    private static final Set<String> PAINT = Set.of("S","s","f","F","f*","B","B*","b","b*");
    record Span(int firstToken, int lastToken, String refusal) { boolean eligible() { return refusal == null; } }
    final List<Object> tokens;
    private final Map<Integer, Span> paths = new HashMap<>();

    PathRepairProgram(PDPage page) throws IOException {
        byte[] content;
        try (var input = page.getContents()) { content = input.readNBytes(4 * 1024 * 1024 + 1); }
        if (content.length > 4 * 1024 * 1024) throw new PdfInputException("This page exceeds the 4 MB decoded content repair limit.");
        tokens = new PDFStreamParser(content).parse();
        if (tokens.size() > 200_000) throw new PdfInputException("This page exceeds the repair token limit.");
        var marked = new ArrayDeque<String>();
        int first = -1, operandsStart = 0, ordinal = -1, graphicsDepth = 0;
        boolean viable = false, text = false;
        String reason = null;
        for (int i = 0; i < tokens.size(); i++) {
            if (!(tokens.get(i) instanceof Operator op)) continue;
            ordinal++;
            String name = op.getName();
            // Inline-image serialization can alter data delimiters; require a separate preservation experiment.
            if (name.equals("BI")) throw new PdfInputException("This page contains an inline image. Artifact repair currently supports pages whose images use separate PDF image objects.");
            if (PATH.containsKey(name)) {
                if (first < 0) {
                    first = operandsStart;
                    viable = (name.equals("m") || name.equals("re")) && !text && marked.isEmpty();
                    reason = !marked.isEmpty() ? (marked.contains("Artifact") ? "This path is already inside an artifact." : "This path belongs to existing marked content.")
                            : "This path cannot be isolated as a complete drawing sequence.";
                }
                if (i - operandsStart != PATH.get(name)) viable = false;
                for (int j = operandsStart; j < i; j++) {
                    if (!(tokens.get(j) instanceof COSNumber number) || !Float.isFinite(number.floatValue())) viable = false;
                }
            } else if (PAINT.contains(name)) {
                paths.put(ordinal, new Span(first, i, first >= 0 && viable && i == operandsStart ? null :
                        (reason == null ? "This drawing has no complete path construction sequence." : reason)));
                first = -1; viable = false; reason = null;
            } else {
                if (first >= 0) { viable = false; reason = "This path includes clipping or interleaved operations that this repair does not support."; }
                switch (name) {
                    case "n" -> { first = -1; viable = false; reason = null; }
                    case "BMC", "BDC" -> {
                        if (i - operandsStart != (name.equals("BMC") ? 1 : 2) || !(tokens.get(operandsStart) instanceof COSName tag)) throw malformed();
                        if (name.equals("BDC") && !(tokens.get(operandsStart+1) instanceof COSName) && !(tokens.get(operandsStart+1) instanceof COSDictionary)) throw malformed();
                        marked.push(tag.getName());
                    }
                    case "EMC" -> { if (i != operandsStart || marked.isEmpty()) throw malformed(); marked.pop(); }
                    case "q" -> { if (i != operandsStart) throw malformed(); graphicsDepth++; }
                    case "Q" -> { if (i != operandsStart || --graphicsDepth < 0) throw malformed(); }
                    case "BT" -> { if (i != operandsStart || text) throw malformed(); text = true; }
                    case "ET" -> { if (i != operandsStart || !text) throw malformed(); text = false; }
                    default -> { }
                }
            }
            operandsStart = i + 1;
        }
        if (!marked.isEmpty() || graphicsDepth != 0 || text || first >= 0 || operandsStart != tokens.size()) throw malformed();
    }

    Span target(int ordinal) {
        return paths.getOrDefault(ordinal, new Span(-1,-1,"This path is inside a reusable form or cannot be mapped to a direct page drawing."));
    }

    byte[] withArtifact(Span span) throws IOException {
        if (!span.eligible()) throw new PdfInputException(span.refusal());
        var modified = new ArrayList<>(tokens.subList(0, span.firstToken()));
        modified.add(COSName.ARTIFACT); modified.add(Operator.getOperator("BMC"));
        modified.addAll(tokens.subList(span.firstToken(), span.lastToken() + 1));
        modified.add(Operator.getOperator("EMC"));
        modified.addAll(tokens.subList(span.lastToken() + 1, tokens.size()));
        return canonical(modified);
    }

    static byte[] canonical(List<Object> tokens) throws IOException {
        var out = new ByteArrayOutputStream(); new ContentStreamWriter(out).writeTokens(tokens); return out.toByteArray();
    }
    private static PdfInputException malformed() { return new PdfInputException("The page has unbalanced or incomplete drawing instructions; artifact repair is unavailable."); }
}
