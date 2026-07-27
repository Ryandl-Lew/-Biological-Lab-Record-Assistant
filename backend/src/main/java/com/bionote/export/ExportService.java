package com.bionote.export;

import com.bionote.collaboration.EventService;
import com.bionote.common.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class ExportService {
    private final JdbcTemplate jdbc;private final ObjectMapper json;private final EventService events;
    public ExportService(JdbcTemplate jdbc,ObjectMapper json,EventService events){this.jdbc=jdbc;this.json=json;this.events=events;}

    public Preview preview(UUID user,UUID recordId){RecordReportModel model=model(user,recordId);String html=html(model);audit(user,recordId,"RECORD_EXPORT_PREVIEW");return new Preview(html,model);}
    public FileExport markdown(UUID user,UUID recordId){RecordReportModel model=model(user,recordId);byte[] bytes=markdown(model).getBytes(StandardCharsets.UTF_8);audit(user,recordId,"RECORD_EXPORT_MARKDOWN");return new FileExport(bytes,safeName(model.title())+"-R"+model.revisionNo()+".md","text/markdown;charset=UTF-8");}
    public FileExport pdf(UUID user,UUID recordId){RecordReportModel model=model(user,recordId);byte[] bytes=pdfBytes(model);audit(user,recordId,"RECORD_EXPORT_PDF");return new FileExport(bytes,safeName(model.title())+"-R"+model.revisionNo()+".pdf","application/pdf");}

    private RecordReportModel model(UUID user,UUID id){
        List<Map<String,Object>> access=jdbc.queryForList("SELECT r.status FROM experiment_records r JOIN project_members pm ON pm.project_id=r.project_id AND pm.user_id=? WHERE r.id=? AND r.deleted_at IS NULL",user.toString(),id.toString());
        if(access.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","记录不存在或无权访问");
        if(!"COMPLETED".equals(access.get(0).get("status")))throw new ApiException(HttpStatus.CONFLICT,"EXPORT_NOT_AVAILABLE","只有已完成记录可以导出");
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT r.*,p.name project_name,u.display_name creator_name,rv.revision_no,rv.snapshot_json,v.decision_comment,v.decided_at,reviewer.display_name reviewer_name FROM experiment_records r JOIN projects p ON p.id=r.project_id JOIN project_members pm ON pm.project_id=r.project_id AND pm.user_id=? JOIN users u ON u.id=r.creator_id JOIN record_revisions rv ON rv.id=r.final_revision_id JOIN reviews v ON v.revision_id=rv.id AND v.status='APPROVED' JOIN users reviewer ON reviewer.id=v.reviewer_id WHERE r.id=? AND r.deleted_at IS NULL AND r.status='COMPLETED'",user.toString(),id.toString());
        if(rows.isEmpty())throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,"INVALID_FINAL_REVISION","最终批准修订数据不完整");
        Map<String,Object> r=rows.get(0),s=decodeMap(r.get("snapshot_json").toString()),template=asMap(s.get("templateSnapshot")),values=asMap(s.get("fieldValues"));List<RecordReportModel.Field> fields=new ArrayList<>();Object fieldData=template.get("fields");if(fieldData instanceof List<?> list)for(Object item:list){Map<String,Object>f=asMap(item);String key=Objects.toString(f.get("fieldKey"),"");fields.add(new RecordReportModel.Field(Objects.toString(f.get("label"),key),display(values.get(key))));}
        List<RecordReportModel.Attachment> attachments=jdbc.query("SELECT a.original_filename,a.size_bytes,u.display_name,a.created_at FROM revision_attachments ra JOIN attachments a ON a.id=ra.attachment_id JOIN users u ON u.id=a.uploader_id WHERE ra.revision_id=? ORDER BY ra.sort_order",(rs,n)->new RecordReportModel.Attachment(rs.getString("original_filename"),rs.getLong("size_bytes"),rs.getString("display_name"),rs.getTimestamp("created_at").toInstant()),r.get("final_revision_id"));
        return new RecordReportModel(Objects.toString(s.get("title")),Objects.toString(s.get("code")),r.get("project_name").toString(),Objects.toString(s.get("experimentType")),LocalDate.parse(Objects.toString(s.get("experimentDate"))),r.get("creator_name").toString(),Objects.toString(s.get("purpose")),fields,Objects.toString(s.get("contentHtml"),""),Objects.toString(s.get("contentPlainText"),""),((Number)r.get("revision_no")).intValue(),r.get("reviewer_name").toString(),((Timestamp)r.get("decided_at")).toInstant(),Objects.toString(r.get("decision_comment"),null),attachments);
    }

    private String html(RecordReportModel m){StringBuilder b=new StringBuilder("<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\"><title>").append(e(m.title())).append("</title><style>body{font-family:system-ui,'Noto Sans SC',sans-serif;color:#172033;max-width:900px;margin:32px auto;padding:0 24px}h1{margin-bottom:4px}.meta{color:#64748b}.grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}.card{border:1px solid #e2e8f0;border-radius:10px;padding:12px}table{width:100%;border-collapse:collapse}th,td{text-align:left;border-bottom:1px solid #e2e8f0;padding:8px;word-break:break-all}.content{margin-top:24px}.review{background:#f8fafc;border-radius:12px;padding:16px;margin-top:24px}@media print{body{margin:0}.card{break-inside:avoid}}</style></head><body><h1>").append(e(m.title())).append("</h1><p class=\"meta\">").append(e(m.code())).append(" · R").append(m.revisionNo()).append(" · ").append(e(m.projectName())).append("</p><div class=\"grid\"><div class=\"card\"><b>实验类型</b><br>").append(e(m.experimentType())).append("</div><div class=\"card\"><b>实验日期</b><br>").append(m.experimentDate()).append("</div><div class=\"card\"><b>创建者</b><br>").append(e(m.creatorName())).append("</div><div class=\"card\"><b>实验目的</b><br>").append(e(m.purpose())).append("</div></div><h2>模板字段</h2><table>");for(var f:m.fields())b.append("<tr><th>").append(e(f.label())).append("</th><td>").append(e(f.value())).append("</td></tr>");b.append("</table><section class=\"content\"><h2>实验正文</h2>").append(m.contentHtml()).append("</section><section class=\"review\"><h2>审核信息</h2><p>审核人：").append(e(m.reviewerName())).append("<br>审核时间：").append(e(m.reviewedAt().toString())).append("<br>审核意见：").append(e(Objects.toString(m.reviewComment(),"无"))).append("</p></section><h2>附件清单</h2><table><tr><th>文件名</th><th>大小</th><th>上传者</th></tr>");for(var a:m.attachments())b.append("<tr><td>").append(e(a.filename())).append("</td><td>").append(a.sizeBytes()).append(" bytes</td><td>").append(e(a.uploaderName())).append("</td></tr>");return b.append("</table></body></html>").toString();}
    private String markdown(RecordReportModel m){StringBuilder b=new StringBuilder("# ").append(m.title()).append("\n\n").append("- 编号：").append(m.code()).append("\n- 项目：").append(m.projectName()).append("\n- 修订：R").append(m.revisionNo()).append("\n- 实验类型：").append(m.experimentType()).append("\n- 实验日期：").append(m.experimentDate()).append("\n- 创建者：").append(m.creatorName()).append("\n\n## 实验目的\n\n").append(m.purpose()).append("\n\n## 模板字段\n\n");for(var f:m.fields())b.append("- **").append(f.label()).append("**：").append(f.value()).append("\n");b.append("\n## 实验正文\n\n").append(m.contentText()).append("\n\n## 审核信息\n\n- 审核人：").append(m.reviewerName()).append("\n- 审核时间：").append(m.reviewedAt()).append("\n- 审核意见：").append(Objects.toString(m.reviewComment(),"无")).append("\n\n## 附件清单\n\n");for(var a:m.attachments())b.append("- ").append(a.filename()).append("（").append(a.sizeBytes()).append(" bytes，").append(a.uploaderName()).append("）\n");return b.toString();}

    private byte[] pdfBytes(RecordReportModel m){try(PDDocument doc=new PDDocument();InputStream fontStream=new ClassPathResource("fonts/NotoSansSC-VF.ttf").getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){PDType0Font font=PDType0Font.load(doc,fontStream);PdfWriter w=new PdfWriter(doc,font);w.title(m.title());w.line("编号："+m.code()+"    项目："+m.projectName()+"    修订：R"+m.revisionNo());w.line("实验类型："+m.experimentType()+"    日期："+m.experimentDate()+"    创建者："+m.creatorName());w.heading("实验目的");w.paragraph(m.purpose());w.heading("模板字段");for(var f:m.fields())w.paragraph(f.label()+"："+f.value());w.heading("实验正文");w.paragraph(m.contentText());w.heading("审核信息");w.paragraph("审核人："+m.reviewerName()+"\n审核时间："+m.reviewedAt()+"\n审核意见："+Objects.toString(m.reviewComment(),"无"));w.heading("附件清单");if(m.attachments().isEmpty())w.line("无");else for(var a:m.attachments())w.paragraph(a.filename()+"（"+a.sizeBytes()+" bytes，"+a.uploaderName()+"）");w.close();doc.save(out);return out.toByteArray();}catch(Exception e){throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,"PDF_GENERATION_FAILED","PDF 生成失败");}}

    private void audit(UUID user,UUID record,String event){UUID project=UUID.fromString(jdbc.queryForObject("SELECT project_id FROM experiment_records WHERE id=?",String.class,record.toString()));events.audit(user,project,record,event,"RECORD",record,Map.of());}
    private Map<String,Object> decodeMap(String v){try{return json.readValue(v,new TypeReference<>(){});}catch(Exception e){throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,"INVALID_REVISION_SNAPSHOT","修订快照损坏");}}
    @SuppressWarnings("unchecked") private Map<String,Object> asMap(Object v){return v instanceof Map<?,?>?(Map<String,Object>)v:Map.of();}
    private String display(Object v){if(v==null)return "—";if(v instanceof Collection<?> c)return String.join("、",c.stream().map(Object::toString).toList());return v.toString();}
    private String e(String v){return HtmlUtils.htmlEscape(Objects.toString(v,""));}
    private String safeName(String v){String s=v.replaceAll("[\\\\/:*?\"<>|]","_").trim();return s.isBlank()?"实验记录":s;}
    public record Preview(String html,RecordReportModel report){}
    public record FileExport(byte[] bytes,String filename,String mediaType){}

    private static final class PdfWriter implements AutoCloseable{
        private final PDDocument doc;private final PDType0Font font;private PDPage page;private PDPageContentStream content;private float y;private final float margin=48,width=PDRectangle.A4.getWidth()-96;
        private PdfWriter(PDDocument doc,PDType0Font font)throws IOException{this.doc=doc;this.font=font;newPage();}
        void title(String text)throws IOException{writeWrapped(text,18,25);y-=8;}
        void heading(String text)throws IOException{ensure(30);y-=8;writeWrapped(text,14,21);}
        void line(String text)throws IOException{writeWrapped(text,10.5f,17);}
        void paragraph(String text)throws IOException{for(String p:Objects.toString(text,"").split("\\R",-1)){if(p.isBlank()){y-=9;ensure(17);}else writeWrapped(p,10.5f,17);}y-=4;}
        private void writeWrapped(String text,float size,float leading)throws IOException{for(String line:wrap(text,size)){ensure(leading);content.beginText();content.setFont(font,size);content.newLineAtOffset(margin,y);content.showText(line);content.endText();y-=leading;}}
        private List<String> wrap(String text,float size)throws IOException{List<String>lines=new ArrayList<>();StringBuilder current=new StringBuilder();for(int offset=0;offset<text.length();){int cp=text.codePointAt(offset);String ch=new String(Character.toChars(cp));String candidate=current+ch;if(font.getStringWidth(candidate)/1000*size>width&&!current.isEmpty()){lines.add(current.toString());current.setLength(0);}current.append(ch);offset+=Character.charCount(cp);}if(!current.isEmpty()||lines.isEmpty())lines.add(current.toString());return lines;}
        private void ensure(float amount)throws IOException{if(y-amount<margin)newPage();}
        private void newPage()throws IOException{if(content!=null)content.close();page=new PDPage(PDRectangle.A4);doc.addPage(page);content=new PDPageContentStream(doc,page);y=PDRectangle.A4.getHeight()-margin;}
        public void close()throws IOException{if(content!=null)content.close();}
    }
}
