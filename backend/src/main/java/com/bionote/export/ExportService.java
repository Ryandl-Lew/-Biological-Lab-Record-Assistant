package com.bionote.export;

import com.bionote.collaboration.CollaborationEvents;
import com.bionote.common.ApiException;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service
public class ExportService implements ExportUseCase {
    private final ExportReportStore reports;private final CollaborationEvents events;
    public ExportService(ExportReportStore reports,CollaborationEvents events){this.reports=reports;this.events=events;}

    public ExportDtos.Preview preview(UUID user,UUID recordId){ExportReportStore.LoadedReport loaded=reports.loadCompleted(user,recordId);RecordReportModel model=loaded.report();String html=html(model);audit(user,recordId,loaded.projectId(),"RECORD_EXPORT_PREVIEW");return new ExportDtos.Preview(html,model);}
    public ExportDtos.FileExport markdown(UUID user,UUID recordId){ExportReportStore.LoadedReport loaded=reports.loadCompleted(user,recordId);RecordReportModel model=loaded.report();byte[] bytes=markdown(model).getBytes(StandardCharsets.UTF_8);audit(user,recordId,loaded.projectId(),"RECORD_EXPORT_MARKDOWN");return new ExportDtos.FileExport(bytes,safeName(model.title())+"-R"+model.revisionNo()+".md","text/markdown;charset=UTF-8");}
    public ExportDtos.FileExport pdf(UUID user,UUID recordId){ExportReportStore.LoadedReport loaded=reports.loadCompleted(user,recordId);RecordReportModel model=loaded.report();byte[] bytes=pdfBytes(model);audit(user,recordId,loaded.projectId(),"RECORD_EXPORT_PDF");return new ExportDtos.FileExport(bytes,safeName(model.title())+"-R"+model.revisionNo()+".pdf","application/pdf");}

    private String html(RecordReportModel m){StringBuilder b=new StringBuilder("<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\"><title>").append(e(m.title())).append("</title><style>body{font-family:system-ui,'Noto Sans SC',sans-serif;color:#172033;max-width:900px;margin:32px auto;padding:0 24px}h1{margin-bottom:4px}.meta{color:#64748b}.grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}.card{border:1px solid #e2e8f0;border-radius:10px;padding:12px}table{width:100%;border-collapse:collapse}th,td{text-align:left;border-bottom:1px solid #e2e8f0;padding:8px;word-break:break-all}.content{margin-top:24px}.review{background:#f8fafc;border-radius:12px;padding:16px;margin-top:24px}@media print{body{margin:0}.card{break-inside:avoid}}</style></head><body><h1>").append(e(m.title())).append("</h1><p class=\"meta\">").append(e(m.code())).append(" · R").append(m.revisionNo()).append(" · ").append(e(m.projectName())).append("</p><div class=\"grid\"><div class=\"card\"><b>实验类型</b><br>").append(e(m.experimentType())).append("</div><div class=\"card\"><b>实验日期</b><br>").append(m.experimentDate()).append("</div><div class=\"card\"><b>创建者</b><br>").append(e(m.creatorName())).append("</div><div class=\"card\"><b>实验目的</b><br>").append(e(m.purpose())).append("</div></div><h2>模板字段</h2><table>");for(var f:m.fields())b.append("<tr><th>").append(e(f.label())).append("</th><td>").append(e(f.value())).append("</td></tr>");b.append("</table><section class=\"content\"><h2>实验正文</h2>").append(m.contentHtml()).append("</section><section class=\"review\"><h2>审核信息</h2><p>审核人：").append(e(m.reviewerName())).append("<br>审核时间：").append(e(m.reviewedAt().toString())).append("<br>审核意见：").append(e(Objects.toString(m.reviewComment(),"无"))).append("</p></section><h2>附件清单</h2><table><tr><th>文件名</th><th>大小</th><th>上传者</th></tr>");for(var a:m.attachments())b.append("<tr><td>").append(e(a.filename())).append("</td><td>").append(a.sizeBytes()).append(" bytes</td><td>").append(e(a.uploaderName())).append("</td></tr>");return b.append("</table></body></html>").toString();}
    private String markdown(RecordReportModel m){StringBuilder b=new StringBuilder("# ").append(m.title()).append("\n\n").append("- 编号：").append(m.code()).append("\n- 项目：").append(m.projectName()).append("\n- 修订：R").append(m.revisionNo()).append("\n- 实验类型：").append(m.experimentType()).append("\n- 实验日期：").append(m.experimentDate()).append("\n- 创建者：").append(m.creatorName()).append("\n\n## 实验目的\n\n").append(m.purpose()).append("\n\n## 模板字段\n\n");for(var f:m.fields())b.append("- **").append(f.label()).append("**：").append(f.value()).append("\n");b.append("\n## 实验正文\n\n").append(m.contentText()).append("\n\n## 审核信息\n\n- 审核人：").append(m.reviewerName()).append("\n- 审核时间：").append(m.reviewedAt()).append("\n- 审核意见：").append(Objects.toString(m.reviewComment(),"无")).append("\n\n## 附件清单\n\n");for(var a:m.attachments())b.append("- ").append(a.filename()).append("（").append(a.sizeBytes()).append(" bytes，").append(a.uploaderName()).append("）\n");return b.toString();}

    private byte[] pdfBytes(RecordReportModel m){try(PDDocument doc=new PDDocument();InputStream fontStream=new ClassPathResource("fonts/NotoSansSC-VF.ttf").getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){PDType0Font font=PDType0Font.load(doc,fontStream);PdfWriter w=new PdfWriter(doc,font);
    w.drawTopBar();w.title(m.title());w.horizontalRule();w.spacing(8);
    String expDate=m.experimentDate()!=null?m.experimentDate().toString():"-";
    w.metadataCard("编号",Objects.toString(m.code(),"-"),"项目",Objects.toString(m.projectName(),"-"));
    w.metadataCard("实验类型",Objects.toString(m.experimentType(),"-"),"实验日期",expDate);
    w.metadataCard("修订版本","R"+m.revisionNo(),"创建者",Objects.toString(m.creatorName(),"-"));
    w.spacing(6);w.horizontalRule();w.spacing(8);
    w.sectionHeading("实验目的");w.paragraph(m.purpose());
    w.spacing(6);w.horizontalRule();w.spacing(8);
    w.sectionHeading("模板字段");
    if(!m.fields().isEmpty()){w.startTable(new float[]{0.35f,0.65f},new String[]{"字段名","字段值"});for(var f:m.fields())w.tableRow(f.label(),f.value());w.endTable();}else w.paragraph("无");
    w.spacing(6);w.horizontalRule();w.spacing(8);
    w.sectionHeading("实验正文");w.paragraph(m.contentText());
    w.spacing(6);w.horizontalRule();w.spacing(8);
    w.sectionHeading("审核信息");
    String reviewedAt=m.reviewedAt()!=null?m.reviewedAt().toString():"-";
    w.drawReviewBox(Objects.toString(m.reviewerName(),"-"),reviewedAt,Objects.toString(m.reviewComment(),"无"));
    w.spacing(6);w.horizontalRule();w.spacing(8);
    w.sectionHeading("附件清单");
    if(m.attachments().isEmpty())w.paragraph("无");
    else{w.startTable(new float[]{0.4f,0.2f,0.4f},new String[]{"文件名","大小","上传者"});for(var a:m.attachments())w.tableRow(a.filename(),formatFileSize(a.sizeBytes()),a.uploaderName());w.endTable();}
    w.close();
    int total=doc.getNumberOfPages();
    for(int i=0;i<total;i++){PDPage pg=doc.getPage(i);try(PDPageContentStream cs=new PDPageContentStream(doc,pg,PDPageContentStream.AppendMode.APPEND,true)){String pt=(i+1)+" / "+total;cs.beginText();cs.setFont(font,8);cs.setNonStrokingColor(0.6f,0.6f,0.6f);float tw=font.getStringWidth(pt)/1000*8;cs.newLineAtOffset((PDRectangle.A4.getWidth()-tw)/2,24);cs.showText(pt);cs.endText();}}
    doc.save(out);return out.toByteArray();}catch(Exception e){throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,"PDF_GENERATION_FAILED","PDF 生成失败");}}

    private String formatFileSize(long bytes){if(bytes<1024)return bytes+" B";if(bytes<1048576)return String.format("%.1f KB",bytes/1024.0);return String.format("%.1f MB",bytes/1048576.0);}
    private void audit(UUID user,UUID record,UUID project,String event){events.audit(user,project,record,event,"RECORD",record,Map.of());}
    private String e(String v){return HtmlUtils.htmlEscape(Objects.toString(v,""));}
    private String safeName(String v){String s=v.replaceAll("[\\\\/:*?\"<>|]","_").trim();return s.isBlank()?"实验记录":s;}
    private static final class PdfWriter implements AutoCloseable{
        private final PDDocument doc;private final PDType0Font font;
        private PDPage page;private PDPageContentStream cs;
        private float y;private final float M=48,W=PDRectangle.A4.getWidth()-96,PH=PDRectangle.A4.getHeight();
        private static final float[] C_PRIMARY={0.059f,0.463f,0.431f};
        private static final float[] C_DARK={0.04f,0.04f,0.06f};
        private static final float[] C_GRAY={0.25f,0.27f,0.33f};
        private static final float[] C_BORDER={0.796f,0.835f,0.882f};
        private static final float[] C_CARD_BG={0.953f,0.965f,0.976f};
        private static final float[] C_TABLE_HDR={0.941f,0.953f,0.965f};
        private static final float[] C_TABLE_ALT={0.973f,0.976f,0.98f};
        private static final float[] C_REVIEW_BG={0.941f,0.965f,0.976f};
        private static final float[] C_HEADER_BAR={0.09f,0.125f,0.2f};
        private float[] tW;private float[] tX;private String[] tH;private int tR;

        PdfWriter(PDDocument doc,PDType0Font font)throws IOException{this.doc=doc;this.font=font;newPage();}
        public void close()throws IOException{if(cs!=null)cs.close();}
        private void newPage()throws IOException{if(cs!=null)cs.close();page=new PDPage(PDRectangle.A4);doc.addPage(page);cs=new PDPageContentStream(doc,page);y=PH-M;}
        private void ensure(float need)throws IOException{if(y-need<M)newPage();}

        void drawTopBar()throws IOException{cs.setNonStrokingColor(C_HEADER_BAR[0],C_HEADER_BAR[1],C_HEADER_BAR[2]);cs.addRect(0,PH-3,PDRectangle.A4.getWidth(),3);cs.fill();y-=12;}

        void title(String text)throws IOException{for(String line:wrap(Objects.toString(text,""),22,W)){ensure(28);tb(line,22,C_DARK,M,y);y-=28;}y-=4;}

        void horizontalRule()throws IOException{ensure(12);cs.setStrokingColor(C_BORDER[0],C_BORDER[1],C_BORDER[2]);cs.setLineWidth(0.5f);cs.moveTo(M,y);cs.lineTo(M+W,y);cs.stroke();y-=10;}

        void spacing(float amount)throws IOException{y-=amount;}

        void sectionHeading(String text)throws IOException{ensure(26);cs.setNonStrokingColor(C_PRIMARY[0],C_PRIMARY[1],C_PRIMARY[2]);cs.addRect(M,y-15,3,17);cs.fill();tb(text,16,C_PRIMARY,M+10,y);y-=24;}

        void metadataCard(String l1,String v1,String l2,String v2)throws IOException{float cardH=48,gap=8,halfW=(W-gap)/2;ensure(cardH+4);float top=y,bot=y-cardH,pad=8;
        cs.setNonStrokingColor(C_CARD_BG[0],C_CARD_BG[1],C_CARD_BG[2]);cs.addRect(M,bot,halfW,cardH);cs.addRect(M+halfW+gap,bot,halfW,cardH);cs.fill();
        cs.setStrokingColor(C_BORDER[0],C_BORDER[1],C_BORDER[2]);cs.setLineWidth(0.5f);cs.addRect(M,bot,halfW,cardH);cs.addRect(M+halfW+gap,bot,halfW,cardH);cs.stroke();
        tb(l1,9,C_GRAY,M+pad,top-15);tb(l2,9,C_GRAY,M+halfW+gap+pad,top-15);
        tb(trunc(v1,halfW-pad*2,12),12,C_DARK,M+pad,top-34);tb(trunc(v2,halfW-pad*2,12),12,C_DARK,M+halfW+gap+pad,top-34);
        y-=(cardH+6);}

        void paragraph(String text)throws IOException{String t=Objects.toString(text,"");if(t.isBlank())return;for(String p:t.split("\\R",-1)){if(p.isBlank()){y-=9;ensure(9);continue;}for(String ln:wrap(p,11,W)){ensure(17);t(ln,11,C_DARK,M,y);y-=17;}}y-=4;}

        void drawReviewBox(String reviewer,String time,String comment)throws IOException{String[]items={"审核人："+reviewer,"审核时间："+time,"审核意见："+comment};float padX=12,padTop=10,padBot=10,innerW=W-padX*2-4,lineH=17;int total=0;for(String it:items)total+=wrap(it,10.5f,innerW).size();float boxH=padTop+padBot+total*lineH;ensure(boxH+4);
        cs.setNonStrokingColor(C_REVIEW_BG[0],C_REVIEW_BG[1],C_REVIEW_BG[2]);cs.addRect(M,y-boxH,W,boxH);cs.fill();
        cs.setNonStrokingColor(C_PRIMARY[0],C_PRIMARY[1],C_PRIMARY[2]);cs.addRect(M,y-boxH,3,boxH);cs.fill();
        cs.setStrokingColor(C_BORDER[0],C_BORDER[1],C_BORDER[2]);cs.setLineWidth(0.5f);cs.addRect(M,y-boxH,W,boxH);cs.stroke();
        float ty=y-padTop-11;for(String it:items)for(String ln:wrap(it,10.5f,innerW)){t(ln,10.5f,C_DARK,M+padX+4,ty);ty-=lineH;}
        y=y-boxH-6;}

        void startTable(float[] widths,String[] headers)throws IOException{tW=widths;tH=headers;tR=0;tX=new float[widths.length];float x=M;for(int i=0;i<widths.length;i++){tX[i]=x;x+=W*widths[i];}drawTableHeader();}
        private void drawTableHeader()throws IOException{float rowH=26;ensure(rowH+2);
        cs.setNonStrokingColor(C_TABLE_HDR[0],C_TABLE_HDR[1],C_TABLE_HDR[2]);cs.addRect(M,y-rowH,W,rowH);cs.fill();
        float ty=y-18,padX=6;for(int i=0;i<tH.length;i++)tb(tH[i],10,C_GRAY,tX[i]+padX,ty);
        cs.setStrokingColor(C_BORDER[0],C_BORDER[1],C_BORDER[2]);cs.setLineWidth(0.5f);
        cs.moveTo(M,y);cs.lineTo(M+W,y);cs.stroke();
        cs.moveTo(M,y-rowH);cs.lineTo(M+W,y-rowH);cs.stroke();
        cs.moveTo(M,y);cs.lineTo(M,y-rowH);cs.stroke();
        cs.moveTo(M+W,y);cs.lineTo(M+W,y-rowH);cs.stroke();
        for(int i=0;i<tX.length-1;i++){cs.moveTo(tX[i]+W*tW[i],y);cs.lineTo(tX[i]+W*tW[i],y-rowH);cs.stroke();}
        y-=rowH;}
        void tableRow(String... cells)throws IOException{float rowH=calcRowH(cells);if(y-rowH-2<M){cs.setStrokingColor(C_BORDER[0],C_BORDER[1],C_BORDER[2]);cs.setLineWidth(0.5f);cs.moveTo(M,y);cs.lineTo(M+W,y);cs.stroke();newPage();drawTableHeader();}
        tR++;float padX=6,padTop=4;if(tR%2==0){cs.setNonStrokingColor(C_TABLE_ALT[0],C_TABLE_ALT[1],C_TABLE_ALT[2]);cs.addRect(M,y-rowH,W,rowH);cs.fill();}
        float cellTop=y-padTop-11;for(int i=0;i<cells.length;i++){float cellW=W*tW[i];float lineY=cellTop;for(String ln:wrap(Objects.toString(cells[i],""),9.5f,cellW-padX*2)){t(ln,9.5f,C_DARK,tX[i]+padX,lineY);lineY-=15;}}
        cs.setStrokingColor(C_BORDER[0],C_BORDER[1],C_BORDER[2]);cs.setLineWidth(0.5f);
        cs.moveTo(M,y-rowH);cs.lineTo(M+W,y-rowH);cs.stroke();
        cs.moveTo(M,y);cs.lineTo(M,y-rowH);cs.stroke();
        cs.moveTo(M+W,y);cs.lineTo(M+W,y-rowH);cs.stroke();
        cs.setLineWidth(0.3f);for(int i=0;i<tX.length-1;i++){cs.moveTo(tX[i]+W*tW[i],y);cs.lineTo(tX[i]+W*tW[i],y-rowH);cs.stroke();}
        y-=rowH;}
        void endTable()throws IOException{tW=null;tX=null;tH=null;tR=0;y-=8;}
        private float calcRowH(String... cells)throws IOException{float m=1;float padX=6;for(int i=0;i<cells.length;i++){float cw=W*tW[i];int n=wrap(Objects.toString(cells[i],""),9.5f,cw-padX*2).size();if(n>m)m=n;}return 8+m*15;}

        private void t(String text,float size,float[] color,float x,float y)throws IOException{cs.beginText();cs.setFont(font,size);cs.setNonStrokingColor(color[0],color[1],color[2]);cs.newLineAtOffset(x,y);cs.showText(text);cs.endText();}

        private void tb(String text,float size,float[] color,float x,float y)throws IOException{cs.beginText();cs.setFont(font,size);cs.setNonStrokingColor(color[0],color[1],color[2]);cs.setStrokingColor(color[0],color[1],color[2]);cs.setLineWidth(size*0.03f);cs.setRenderingMode(RenderingMode.FILL_STROKE);cs.newLineAtOffset(x,y);cs.showText(text);cs.endText();cs.setRenderingMode(RenderingMode.FILL);}

        private List<String> wrap(String text,float size,float maxW)throws IOException{List<String>lines=new ArrayList<>();StringBuilder sb=new StringBuilder();for(int offset=0;offset<text.length();){int cp=text.codePointAt(offset);String ch=new String(Character.toChars(cp));String cand=sb+ch;if(font.getStringWidth(cand)/1000*size>maxW&&!sb.isEmpty()){lines.add(sb.toString());sb.setLength(0);}sb.append(ch);offset+=Character.charCount(cp);}if(!sb.isEmpty()||lines.isEmpty())lines.add(sb.toString());return lines;}

        private String trunc(String text,float maxW,float size)throws IOException{if(text==null||text.isBlank())return "";if(font.getStringWidth(text)/1000*size<=maxW)return text;String dot="...";StringBuilder sb=new StringBuilder();for(int offset=0;offset<text.length();){int cp=text.codePointAt(offset);String ch=new String(Character.toChars(cp));if(font.getStringWidth(sb+dot)/1000*size>maxW)return sb+dot;sb.append(ch);offset+=Character.charCount(cp);}return sb.toString();}
    }
}
