package com.bionote.review;

import com.bionote.collaboration.EventService;
import com.bionote.common.ApiException;
import com.bionote.domain.record.Decision;
import com.bionote.domain.record.RecordActionPolicy;
import com.bionote.domain.record.RecordContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class ReviewService {
    private final JdbcTemplate jdbc; private final ObjectMapper json; private final EventService events; private final RecordActionPolicy actionPolicy;
    public ReviewService(JdbcTemplate jdbc,ObjectMapper json,EventService events,RecordActionPolicy actionPolicy){this.jdbc=jdbc;this.json=json;this.events=events;this.actionPolicy=actionPolicy;}

    public List<ReviewDtos.Candidate> candidates(UUID user,UUID recordId){
        Map<String,Object> r=record(recordId,false);requireMember(user,r);
        return jdbc.query("SELECT pm.user_id,u.display_name,pm.role FROM project_members pm JOIN users u ON u.id=pm.user_id WHERE pm.project_id=? AND pm.role IN ('OWNER','REVIEWER') AND pm.user_id<>? ORDER BY CASE pm.role WHEN 'OWNER' THEN 0 ELSE 1 END,u.display_name",
                (rs,n)->new ReviewDtos.Candidate(UUID.fromString(rs.getString("user_id")),rs.getString("display_name"),rs.getString("role")),r.get("project_id"),r.get("creator_id"));
    }

    @Transactional public ReviewDtos.RevisionView submit(UUID user,UUID recordId,ReviewDtos.SubmitRequest request,String idempotencyKey){
        String key=normalizeKey(idempotencyKey);
        if(key!=null){List<String> existing=jdbc.queryForList("SELECT id FROM record_revisions WHERE record_id=? AND idempotency_key=?",String.class,recordId.toString(),key);if(!existing.isEmpty())return revision(user,UUID.fromString(existing.get(0)));}
        Map<String,Object> r=record(recordId,true);requireCreatorAndActive(user,r);
        if(Boolean.TRUE.equals(r.get("provisional")))conflict("请先保存记录再提交审核");
        if(!Set.of("IN_PROGRESS","CHANGES_REQUESTED").contains(r.get("status").toString()))conflict("当前记录不能提交审核");
        if(((Number)r.get("version")).longValue()!=request.expectedRecordVersion())throw new ApiException(HttpStatus.CONFLICT,"OPTIMISTIC_LOCK_CONFLICT","记录已被其他页面更新");
        validateReviewer(r,request.reviewerId());Map<String,Object> normalizedValues=validateRequiredAndFiles(r);String normalizedJson=encode(normalizedValues);if(!normalizedJson.equals(r.get("field_values_json").toString())){jdbc.update("UPDATE experiment_records SET field_values_json=? WHERE id=?",normalizedJson,recordId.toString());r.put("field_values_json",normalizedJson);}
        if(jdbc.queryForObject("SELECT COUNT(*) FROM reviews WHERE record_id=? AND status='PENDING'",Integer.class,recordId.toString())>0)conflict("记录已有待处理审核");
        int no=((Number)r.get("current_revision_no")).intValue()+1;UUID revisionId=UUID.randomUUID(),reviewId=UUID.randomUUID();Instant now=Instant.now();String encoded=encode(snapshot(r));String hash=sha256(encoded);
        try{jdbc.update("INSERT INTO record_revisions(id,record_id,revision_no,snapshot_json,content_hash,submit_note,submitted_by,submitted_at,idempotency_key) VALUES(?,?,?,?,?,?,?,?,?)",revisionId.toString(),recordId.toString(),no,encoded,hash,trim(request.submitNote()),user.toString(),Timestamp.from(now),key);}
        catch(DuplicateKeyException ex){if(key!=null){String id=jdbc.queryForObject("SELECT id FROM record_revisions WHERE record_id=? AND idempotency_key=?",String.class,recordId.toString(),key);return revision(user,UUID.fromString(id));}throw ex;}
        List<String> attachments=jdbc.queryForList("SELECT id FROM attachments WHERE record_id=? AND deleted_at IS NULL ORDER BY created_at,id",String.class,recordId.toString());
        for(int i=0;i<attachments.size();i++)jdbc.update("INSERT INTO revision_attachments(revision_id,attachment_id,sort_order) VALUES(?,?,?)",revisionId.toString(),attachments.get(i),i);
        jdbc.update("INSERT INTO reviews(id,record_id,revision_id,reviewer_id,status,assigned_at) VALUES(?,?,?,?, 'PENDING',?)",reviewId.toString(),recordId.toString(),revisionId.toString(),request.reviewerId().toString(),Timestamp.from(now));
        int changed=jdbc.update("UPDATE experiment_records SET status='IN_REVIEW',current_revision_no=?,current_review_id=?,updated_at=?,version=version+1 WHERE id=? AND version=? AND status IN ('IN_PROGRESS','CHANGES_REQUESTED')",no,reviewId.toString(),Timestamp.from(now),recordId.toString(),request.expectedRecordVersion());
        if(changed!=1)throw new ApiException(HttpStatus.CONFLICT,"OPTIMISTIC_LOCK_CONFLICT","记录已被其他页面提交");
        UUID project=UUID.fromString(r.get("project_id").toString());
        events.notify(request.reviewerId(),"REVIEW_ASSIGNED","新的审核任务",r.get("title")+" 已提交 R"+no,Map.of("recordId",recordId.toString(),"reviewId",reviewId.toString(),"revisionNo",no),"review-assigned:"+reviewId);
        events.audit(user,project,recordId,"RECORD_SUBMITTED","REVISION",revisionId,Map.of("revisionNo",no,"reviewerId",request.reviewerId().toString()));
        return revision(user,revisionId);
    }

    @Transactional public ReviewDtos.RevisionView requestChanges(UUID user,UUID recordId,UUID reviewId,String comment){
        String value=trim(comment);if(value==null)throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","退回修改必须填写意见",Map.of("comment","退回意见不能为空"));
        return decide(user,recordId,reviewId,"CHANGES_REQUESTED",value);
    }
    @Transactional public ReviewDtos.RevisionView approve(UUID user,UUID recordId,UUID reviewId,String comment){return decide(user,recordId,reviewId,"APPROVED",trim(comment));}

    private ReviewDtos.RevisionView decide(UUID user,UUID recordId,UUID reviewId,String decision,String comment){
        Map<String,Object> r=record(recordId,true),review=reviewRow(reviewId,true);
        if(!review.get("record_id").toString().equals(recordId.toString())||!review.get("reviewer_id").toString().equals(user.toString()))throw new ApiException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","只有指定审核人可以处理该审核");
        if(!"ACTIVE".equals(r.get("project_status")))conflict("项目已归档，不能执行审核");
        if(!"IN_REVIEW".equals(r.get("status"))||!reviewId.toString().equals(Objects.toString(r.get("current_review_id"),""))||!"PENDING".equals(review.get("status")))conflict("该审核已处理或不是当前轮次");
        Instant now=Instant.now();int changed=jdbc.update("UPDATE reviews SET status=?,decision_comment=?,decided_at=? WHERE id=? AND status='PENDING'",decision,comment,Timestamp.from(now),reviewId.toString());if(changed!=1)conflict("该审核已被处理");
        UUID revision=UUID.fromString(review.get("revision_id").toString());
        if("APPROVED".equals(decision))jdbc.update("UPDATE experiment_records SET status='COMPLETED',final_revision_id=?,updated_at=?,version=version+1 WHERE id=? AND current_review_id=? AND status='IN_REVIEW'",revision.toString(),Timestamp.from(now),recordId.toString(),reviewId.toString());
        else jdbc.update("UPDATE experiment_records SET status='CHANGES_REQUESTED',updated_at=?,version=version+1 WHERE id=? AND current_review_id=? AND status='IN_REVIEW'",Timestamp.from(now),recordId.toString(),reviewId.toString());
        UUID creator=UUID.fromString(r.get("creator_id").toString()),project=UUID.fromString(r.get("project_id").toString());int no=jdbc.queryForObject("SELECT revision_no FROM record_revisions WHERE id=?",Integer.class,revision.toString());
        events.notify(creator,"APPROVED".equals(decision)?"REVIEW_APPROVED":"CHANGES_REQUESTED","APPROVED".equals(decision)?"审核已通过":"记录被退回修改",r.get("title")+" 的 R"+no+("APPROVED".equals(decision)?" 已通过":" 需要修改"),Map.of("recordId",recordId.toString(),"reviewId",reviewId.toString(),"revisionNo",no),"review-decision:"+reviewId);
        events.audit(user,project,recordId,"APPROVED".equals(decision)?"REVIEW_APPROVED":"REVIEW_CHANGES_REQUESTED","REVIEW",reviewId,Map.of("revisionNo",no,"comment",comment==null?"":comment));
        return revision(user,revision);
    }

    public List<ReviewDtos.RevisionView> revisions(UUID user,UUID recordId){Map<String,Object> r=record(recordId,false);requireMember(user,r);return jdbc.queryForList("SELECT id FROM record_revisions WHERE record_id=? ORDER BY revision_no",recordId.toString()).stream().map(x->revision(user,UUID.fromString(x.get("id").toString()))).toList();}

    public ReviewDtos.RevisionView revision(UUID user,UUID revisionId){
        Map<String,Object> rv=row("SELECT * FROM record_revisions WHERE id=?",revisionId,"修订不存在");Map<String,Object> r=record(UUID.fromString(rv.get("record_id").toString()),false);requireMember(user,r);Map<String,Object> review=row("SELECT * FROM reviews WHERE revision_id=?",revisionId,"审核不存在");
        String reviewerName=jdbc.queryForObject("SELECT display_name FROM users WHERE id=?",String.class,review.get("reviewer_id")),submitter=jdbc.queryForObject("SELECT display_name FROM users WHERE id=?",String.class,rv.get("submitted_by"));
        List<Map<String,Object>> attachments=jdbc.query("SELECT a.id,a.original_filename filename,a.media_type mediaType,a.size_bytes sizeBytes,a.previewable FROM revision_attachments ra JOIN attachments a ON a.id=ra.attachment_id WHERE ra.revision_id=? ORDER BY ra.sort_order",(rs,n)->Map.of("id",rs.getString("id"),"filename",rs.getString("filename"),"mediaType",rs.getString("mediaType"),"sizeBytes",rs.getLong("sizeBytes"),"previewable",rs.getBoolean("previewable")),revisionId.toString());
        ReviewDtos.ReviewView reviewView=new ReviewDtos.ReviewView(UUID.fromString(review.get("id").toString()),revisionId,UUID.fromString(review.get("reviewer_id").toString()),reviewerName,review.get("status").toString(),Objects.toString(review.get("decision_comment"),null),((Timestamp)review.get("assigned_at")).toInstant(),review.get("decided_at")==null?null:((Timestamp)review.get("decided_at")).toInstant(),review.get("reviewer_id").toString().equals(user.toString())&&"PENDING".equals(review.get("status")));
        return new ReviewDtos.RevisionView(revisionId,((Number)rv.get("revision_no")).intValue(),decode(rv.get("snapshot_json").toString()),rv.get("content_hash").toString(),Objects.toString(rv.get("submit_note"),null),UUID.fromString(rv.get("submitted_by").toString()),submitter,((Timestamp)rv.get("submitted_at")).toInstant(),reviewView,attachments);
    }

    public List<ReviewDtos.PendingReview> pending(UUID user){return jdbc.query("SELECT v.id review_id,r.id record_id,r.code,r.title,r.project_id,p.name project_name,rv.revision_no,v.assigned_at FROM reviews v JOIN experiment_records r ON r.id=v.record_id JOIN projects p ON p.id=r.project_id JOIN record_revisions rv ON rv.id=v.revision_id JOIN project_members pm ON pm.project_id=r.project_id AND pm.user_id=? WHERE v.reviewer_id=? AND v.status='PENDING' AND r.status='IN_REVIEW' ORDER BY v.assigned_at",(rs,n)->new ReviewDtos.PendingReview(UUID.fromString(rs.getString("review_id")),UUID.fromString(rs.getString("record_id")),rs.getString("code"),rs.getString("title"),UUID.fromString(rs.getString("project_id")),rs.getString("project_name"),rs.getInt("revision_no"),rs.getTimestamp("assigned_at").toInstant()),user.toString(),user.toString());}

    private Map<String,Object> snapshot(Map<String,Object> r){Map<String,Object> m=new LinkedHashMap<>();m.put("id",r.get("id").toString());m.put("code",r.get("code"));m.put("projectId",r.get("project_id").toString());m.put("creatorId",r.get("creator_id").toString());m.put("title",r.get("title"));m.put("experimentType",r.get("experiment_type"));m.put("experimentDate",r.get("experiment_date").toString());m.put("purpose",r.get("purpose"));m.put("templateSnapshot",decode(r.get("template_snapshot_json").toString()));m.put("fieldValues",decode(r.get("field_values_json").toString()));m.put("contentJson",decode(r.get("content_json").toString()));m.put("contentHtml",r.get("content_html_sanitized"));m.put("contentPlainText",r.get("content_plain_text"));return m;}
    private Map<String,Object> validateRequiredAndFiles(Map<String,Object> r){Map<String,Object> snapshot=decodeMap(r.get("template_snapshot_json").toString()),values=decodeMap(r.get("field_values_json").toString());Object fields=snapshot.get("fields");if(!(fields instanceof List<?> list))return values;Map<String,String> errors=new LinkedHashMap<>();for(Object o:list){if(!(o instanceof Map<?,?> f))continue;String key=Objects.toString(f.get("fieldKey"),""),label=Objects.toString(f.get("label"),key),type=Objects.toString(f.get("fieldType"),"");Object value=values.get(key);if("FILE".equals(type)&&value!=null){Collection<?> ids=value instanceof Collection<?> c?c:List.of(value);List<String> active=new ArrayList<>();for(Object id:ids){if(id==null)continue;UUID uuid;try{uuid=UUID.fromString(id.toString());}catch(IllegalArgumentException ignored){continue;}Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM attachments WHERE id=? AND record_id=? AND deleted_at IS NULL",Integer.class,uuid.toString(),r.get("id"));if(count!=null&&count==1)active.add(uuid.toString());}if(active.isEmpty())values.remove(key);else values.put(key,active);value=values.get(key);}boolean empty=value==null||value.toString().isBlank()||(value instanceof Collection<?> c&&c.isEmpty());if(Boolean.TRUE.equals(f.get("required"))&&empty)errors.put(key,label+"不能为空");}if(!errors.isEmpty())throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","请补全必填字段",errors);return values;}
    private void validateReviewer(Map<String,Object> r,UUID reviewer){if(r.get("creator_id").toString().equals(reviewer.toString()))throw new ApiException(HttpStatus.BAD_REQUEST,"SELF_REVIEW_NOT_ALLOWED","不能审核自己创建的记录");List<String> roles=jdbc.queryForList("SELECT role FROM project_members WHERE project_id=? AND user_id=?",String.class,r.get("project_id"),reviewer.toString());if(roles.isEmpty()||!Set.of("OWNER","REVIEWER").contains(roles.get(0)))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_REVIEWER","审核人必须是项目负责人或审核者");}
    private Map<String,Object> record(UUID id,boolean lock){List<Map<String,Object>> rows=jdbc.queryForList("SELECT r.*,p.status project_status FROM experiment_records r JOIN projects p ON p.id=r.project_id WHERE r.id=? AND r.deleted_at IS NULL"+(lock?" FOR UPDATE":""),id.toString());if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","记录不存在");return rows.get(0);}
    private Map<String,Object> reviewRow(UUID id,boolean lock){return row("SELECT * FROM reviews WHERE id=?"+(lock?" FOR UPDATE":""),id,"审核不存在");}
    private Map<String,Object> row(String sql,UUID id,String message){List<Map<String,Object>> rows=jdbc.queryForList(sql,id.toString());if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND",message);return rows.get(0);}
    private void requireMember(UUID user,Map<String,Object> r){if(jdbc.queryForObject("SELECT COUNT(*) FROM project_members WHERE project_id=? AND user_id=?",Integer.class,r.get("project_id"),user.toString())==0)throw new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","记录不存在或无权访问");}
    private void requireCreatorAndActive(UUID user,Map<String,Object> r){List<String> roles=jdbc.queryForList("SELECT role FROM project_members WHERE project_id=? AND user_id=?",String.class,r.get("project_id"),user.toString());String role=roles.isEmpty()?null:roles.get(0);Decision decision=actionPolicy.canSubmit(new RecordContext(UUID.fromString(r.get("id").toString()),UUID.fromString(r.get("project_id").toString()),UUID.fromString(r.get("creator_id").toString()),r.get("status").toString(),r.get("project_status").toString(),false,Boolean.TRUE.equals(r.get("provisional")),role),user);if(!decision.allowed()){HttpStatus status="RESOURCE_NOT_FOUND".equals(decision.errorCode())?HttpStatus.NOT_FOUND:"ACCESS_DENIED".equals(decision.errorCode())?HttpStatus.FORBIDDEN:HttpStatus.CONFLICT;throw new ApiException(status,decision.errorCode(),decision.message());}}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException(e);}}
    private Object decode(String value){try{return json.readValue(value,Object.class);}catch(Exception e){return Map.of();}}
    private Map<String,Object> decodeMap(String value){try{return json.readValue(value,new TypeReference<>(){});}catch(Exception e){return new LinkedHashMap<>();}}
    private String sha256(String value){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private String trim(String v){return v==null||v.isBlank()?null:v.trim();}
    private String normalizeKey(String v){String key=trim(v);if(key==null)return null;if(key.length()>160)throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Idempotency-Key 过长");return key;}
    private void conflict(String message){throw new ApiException(HttpStatus.CONFLICT,"REVIEW_STATE_CONFLICT",message);}
}
