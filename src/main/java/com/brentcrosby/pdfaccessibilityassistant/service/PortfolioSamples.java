package com.brentcrosby.pdfaccessibilityassistant.service;

import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.documentinterchange.markedcontent.PDPropertyList;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

/** Synthetic, reproducible demo/corpus definitions. No binary fixture belongs in Git. */
public final class PortfolioSamples {
    public record Label(String text,String role) {}
    public record Sample(String id,byte[] pdf,List<Label> labels) {}
    private PortfolioSamples() {}
    public static Sample untagged(int variant) {
        var labels=variant==0 ? List.of(new Label("Accessible documents for everyone","H1"),new Label("This paragraph explains the purpose of the document.","P"),
                new Label("Review steps","H2"),new Label("1. Inspect the reading order.","LI"),new Label("2. Describe meaningful figures.","LI"),
                new Label("Figure 1. Quarterly participation.","Caption"),new Label("The final paragraph explains what happens next.","P"))
                :List.of(new Label("A practical review guide","H1"),new Label("Use the visual and semantic views together.","P"),new Label("IMPORTANT CONTEXT","H2"),
                new Label("Figure 7 is discussed in the next paragraph.","P"),new Label("- Check every proposed role.","LI"),new Label("Table 2. Summary of findings.","Caption"),new Label("Finish with a human review of meaning and order.","P"));
        try(var doc=new PDDocument();var out=new ByteArrayOutputStream()) {
            setup(doc,variant+10,"Untagged tagging demo");var page=new PDPage(new PDRectangle(600,780));doc.addPage(page);
            try(var stream=new PDPageContentStream(doc,page)) {
                int i=0;for(var label:labels) {float size=i==0?26:(variant==0 && i==2?16:12);text(stream,50,710-i*55,size,label.text());i++;}
                stream.setStrokingColor(new Color(20,92,102));stream.setLineWidth(2);stream.moveTo(50,740);stream.lineTo(550,740);stream.stroke();
            }
            doc.save(out);return new Sample("layout-"+(variant+1),out.toByteArray(),labels);
        }catch(IOException ex){throw new IllegalStateException(ex);}
    }
    public static byte[] semantic() {
        try(var doc=new PDDocument();var out=new ByteArrayOutputStream()) {
            setup(doc,3,"Semantic repair demonstration");doc.getDocumentCatalog().setLanguage("en");
            var page=new PDPage(new PDRectangle(600,780));doc.addPage(page);
            var root=new COSDictionary();root.setItem(COSName.TYPE,COSName.STRUCT_TREE_ROOT);
            var container=element("Document",root);root.setItem(COSName.K,container);var children=new COSArray();container.setItem(COSName.K,children);
            var owners=new COSArray();var tags=new ArrayList<COSDictionary>();
            for(String role:List.of("H1","P","Figure","Caption")) {var e=element(role,container);e.setItem(COSName.PG,page);e.setInt(COSName.K,tags.size());tags.add(e);owners.add(e);}
            // Intentionally wrong sibling order for the reading-order demo.
            children.add(tags.get(1));children.add(tags.get(0));children.add(tags.get(2));children.add(tags.get(3));
            var nums=new COSArray();nums.add(COSInteger.ZERO);nums.add(owners);var tree=new COSDictionary();tree.setItem(COSName.NUMS,nums);
            root.setItem(COSName.PARENT_TREE,tree);root.setInt(COSName.PARENT_TREE_NEXT_KEY,1);page.getCOSObject().setInt(COSName.STRUCT_PARENTS,0);
            doc.getDocumentCatalog().getCOSObject().setItem(COSName.STRUCT_TREE_ROOT,root);var mark=new COSDictionary();mark.setBoolean(COSName.getPDFName("Marked"),true);doc.getDocumentCatalog().getCOSObject().setItem(COSName.MARK_INFO,mark);
            try(var stream=new PDPageContentStream(doc,page)) {
                begin(stream,"H1",0);text(stream,50,710,26,"Community participation");stream.endMarkedContent();
                begin(stream,"P",1);text(stream,50,655,12,"Participation increased each quarter. Describe the chart below.");stream.endMarkedContent();
                begin(stream,"Figure",2);
                var image=new BufferedImage(400,180,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,400,180);
                g.setColor(new Color(20,92,102));g.fillRect(30,100,70,60);g.fillRect(140,65,70,95);g.fillRect(250,20,70,140);g.dispose();
                stream.drawImage(LosslessFactory.createFromImage(doc,image),60,400,400,180);stream.endMarkedContent();
                begin(stream,"Caption",3);text(stream,50,365,12,"Figure 1. Participation: 30, 48 and 70 people.");stream.endMarkedContent();
                text(stream,50,300,12,"This untagged paragraph needs a reviewed text role.");
                stream.setStrokingColor(new Color(20,92,102));stream.setLineWidth(2);stream.moveTo(50,270);stream.lineTo(550,270);stream.stroke();
                stream.beginMarkedContent(COSName.ARTIFACT);text(stream,50,50,10,"Synthetic demo - page 1");stream.endMarkedContent();
            }
            doc.save(out);return out.toByteArray();
        }catch(IOException ex){throw new IllegalStateException(ex);}
    }
    private static void setup(PDDocument doc,int seed,String title) {
        doc.getDocumentInformation().setTitle(title);var id=new COSArray();byte[] bytes=new byte[16];Arrays.fill(bytes,(byte)seed);id.add(new COSString(bytes));id.add(new COSString(bytes));doc.getDocument().setDocumentID(id);
    }
    private static COSDictionary element(String role,COSDictionary parent){var d=new COSDictionary();d.setItem(COSName.TYPE,COSName.STRUCT_ELEM);d.setName(COSName.S,role);d.setItem(COSName.P,parent);return d;}
    private static void begin(PDPageContentStream stream,String role,int mcid)throws IOException {var props=new COSDictionary();props.setInt(COSName.MCID,mcid);stream.beginMarkedContent(COSName.getPDFName(role),PDPropertyList.create(props));}
    private static void text(PDPageContentStream stream,float x,float y,float size,String text)throws IOException {
        stream.beginText();stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),size);stream.newLineAtOffset(x,y);stream.showText(text);stream.endText();
    }
}
