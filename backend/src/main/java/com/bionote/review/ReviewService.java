package com.bionote.review;

import com.bionote.collaboration.CollaborationEvents;
import com.bionote.common.ApiException;
import com.bionote.attachment.AttachmentStore;
import com.bionote.domain.record.Decision;
import com.bionote.domain.record.RecordActionPolicy;
import com.bionote.domain.record.RecordContext;
import com.bionote.project.ProjectMemberStore;
import com.bionote.project.ProjectStore;
import com.bionote.record.RecordJsonCodec;
import com.bionote.record.RecordStore;
import com.bionote.revision.RevisionAppender;
import com.bionote.revision.RevisionAttachmentAppender;
import com.bionote.revision.RevisionLookup;
import com.bionote.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class ReviewService implements ReviewUseCase {
    private final RecordStore records;private final ProjectStore projects;private final ProjectMemberStore members;
    private final AttachmentStore attachments;private final RevisionAppender revisionAppender;
    private final RevisionLookup revisionLookup;private final RevisionAttachmentAppender revisionAttachments;
    private final ReviewStore reviews;private final UserRepository users;private final RecordJsonCodec json;
    private final CollaborationEvents events;private final RecordActionPolicy actionPolicy;
    public ReviewService(RecordStore records,ProjectStore projects,ProjectMemberStore members,AttachmentStore attachments,
                         RevisionAppender revisionAppender,RevisionLookup revisionLookup,
                         RevisionAttachmentAppender revisionAttachments,ReviewStore reviews,UserRepository users,
                         RecordJsonCodec json,CollaborationEvents events,RecordActionPolicy actionPolicy){
        this.records=records;this.projects=projects;this.members=members;this.attachments=attachments;
        this.revisionAppender=revisionAppender;this.revisionLookup=revisionLookup;this.revisionAttachments=revisionAttachments;
        this.reviews=reviews;this.users=users;this.json=json;this.events=events;this.actionPolicy=actionPolicy;
    }

    public List<ReviewDtos.Candidate> candidates(UUID user,UUID recordId){
        RecordStore.RecordData record=record(recordId,false);requireMember(user,record);
        return members.list(record.projectId()).stream().filter(member->Set.of("OWNER","REVIEWER").contains(member.role()))
                .filter(member->!member.userId().equals(record.creatorId()))
                .map(member->new ReviewDtos.Candidate(member.userId(),member.displayName(),member.role())).toList();
    }

    @Transactional public ReviewDtos.RevisionView submit(UUID user,UUID recordId,ReviewDtos.SubmitRequest request,String idempotencyKey){
        String key=normalizeKey(idempotencyKey);
        if(key!=null){var existing=revisionLookup.findByRecordAndIdempotencyKey(recordId,key);if(existing.isPresent())return revision(user,existing.get().id());}
        RecordStore.RecordData record=record(recordId,true);
        if(key!=null){var existing=revisionLookup.findByRecordAndIdempotencyKey(recordId,key);if(existing.isPresent())return revision(user,existing.get().id());}
        requireCreatorAndActive(user,record);
        if(record.provisional())conflict("请先保存记录再提交审核");
        if(!Set.of("IN_PROGRESS","CHANGES_REQUESTED").contains(record.status()))conflict("当前记录不能提交审核");
        if(record.version()!=request.expectedRecordVersion())throw new ApiException(HttpStatus.CONFLICT,"OPTIMISTIC_LOCK_CONFLICT","记录已被其他页面更新");
        validateReviewer(record,request.reviewerId());Map<String,Object> normalizedValues=validateRequiredAndFiles(record);
        String normalizedJson=json.encode(normalizedValues);if(!normalizedJson.equals(record.fieldValuesJson()))records.replaceFieldValuesWithoutVersion(recordId,normalizedJson);
        if(reviews.existsPending(recordId))conflict("记录已有待处理审核");
        int no=record.currentRevisionNo()+1;UUID revisionId=UUID.randomUUID(),reviewId=UUID.randomUUID();Instant now=Instant.now();
        String encoded=json.encode(snapshot(record,normalizedJson));String hash=sha256(encoded);
        revisionAppender.append(new RevisionAppender.RevisionRecord(revisionId,recordId,no,encoded,1,hash,trim(request.submitNote()),user,now,key));
        List<UUID> attachmentIds=attachments.findActiveByRecord(recordId).stream().map(AttachmentStore.AttachmentRecord::id).toList();
        revisionAttachments.append(revisionId,attachmentIds);
        reviews.append(new ReviewStore.ReviewRecord(reviewId,recordId,revisionId,request.reviewerId(),"PENDING",null,now,null));
        if(!records.markSubmitted(recordId,request.expectedRecordVersion(),no,reviewId,now))
            throw new ApiException(HttpStatus.CONFLICT,"OPTIMISTIC_LOCK_CONFLICT","记录已被其他页面提交");
        UUID project=record.projectId();
        events.notify(request.reviewerId(),"REVIEW_ASSIGNED","新的审核任务",record.title()+" 已提交 R"+no,Map.of("recordId",recordId.toString(),"reviewId",reviewId.toString(),"revisionNo",no),"review-assigned:"+reviewId);
        events.audit(user,project,recordId,"RECORD_SUBMITTED","REVISION",revisionId,Map.of("revisionNo",no,"reviewerId",request.reviewerId().toString()));
        return revision(user,revisionId);
    }

    @Transactional public ReviewDtos.RevisionView requestChanges(UUID user,UUID recordId,UUID reviewId,String comment){
        String value=trim(comment);if(value==null)throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","退回修改必须填写意见",Map.of("comment","退回意见不能为空"));
        return decide(user,recordId,reviewId,"CHANGES_REQUESTED",value);
    }
    @Transactional public ReviewDtos.RevisionView approve(UUID user,UUID recordId,UUID reviewId,String comment){return decide(user,recordId,reviewId,"APPROVED",trim(comment));}

    private ReviewDtos.RevisionView decide(UUID user,UUID recordId,UUID reviewId,String decision,String comment){
        RecordStore.RecordData record=record(recordId,true);ReviewStore.ReviewRecord review=reviews.findByIdForUpdate(reviewId)
                .orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","审核不存在"));
        if(!review.recordId().equals(recordId)||!review.reviewerId().equals(user))throw new ApiException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","只有指定审核人可以处理该审核");
        if(!"ACTIVE".equals(projectStatus(record.projectId())))conflict("项目已归档，不能执行审核");
        if(!"IN_REVIEW".equals(record.status())||!reviewId.equals(record.currentReviewId())||!"PENDING".equals(review.status()))conflict("该审核已处理或不是当前轮次");
        Instant now=Instant.now();if(!reviews.decidePending(reviewId,decision,comment,now))conflict("该审核已被处理");
        UUID revision=review.revisionId();boolean changed="APPROVED".equals(decision)
                ?records.markReviewApproved(recordId,reviewId,revision,now)
                :records.markReviewChangesRequested(recordId,reviewId,now);
        if(!changed)conflict("该审核已处理或不是当前轮次");
        UUID creator=record.creatorId(),project=record.projectId();int no=revisionLookup.findById(revision).orElseThrow().revisionNo();
        events.notify(creator,"APPROVED".equals(decision)?"REVIEW_APPROVED":"CHANGES_REQUESTED","APPROVED".equals(decision)?"审核已通过":"记录被退回修改",record.title()+" 的 R"+no+("APPROVED".equals(decision)?" 已通过":" 需要修改"),Map.of("recordId",recordId.toString(),"reviewId",reviewId.toString(),"revisionNo",no),"review-decision:"+reviewId);
        events.audit(user,project,recordId,"APPROVED".equals(decision)?"REVIEW_APPROVED":"REVIEW_CHANGES_REQUESTED","REVIEW",reviewId,Map.of("revisionNo",no,"comment",comment==null?"":comment));
        return revision(user,revision);
    }

    public List<ReviewDtos.RevisionView> revisions(UUID user,UUID recordId){RecordStore.RecordData record=record(recordId,false);requireMember(user,record);return revisionLookup.findIdsByRecordOrderByRevisionNo(recordId).stream().map(id->revision(user,id)).toList();}

    public ReviewDtos.RevisionView revision(UUID user,UUID revisionId){
        RevisionLookup.RevisionRecord revision=revisionLookup.findById(revisionId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","修订不存在"));
        RecordStore.RecordData record=record(revision.recordId(),false);requireMember(user,record);
        ReviewStore.ReviewRecord review=reviews.findByRevisionId(revisionId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","审核不存在"));
        String reviewerName=users.findById(review.reviewerId()).orElseThrow().getDisplayName(),submitter=users.findById(revision.submittedBy()).orElseThrow().getDisplayName();
        List<Map<String,Object>> attachmentViews=attachments.findByRevision(revisionId).stream().map(value->Map.<String,Object>of(
                "id",value.id().toString(),"filename",value.originalFilename(),"mediaType",value.mediaType(),
                "sizeBytes",value.sizeBytes(),"previewable",value.previewable())).toList();
        ReviewDtos.ReviewView reviewView=new ReviewDtos.ReviewView(review.id(),revisionId,review.reviewerId(),reviewerName,review.status(),review.decisionComment(),review.assignedAt(),review.decidedAt(),review.reviewerId().equals(user)&&"PENDING".equals(review.status()));
        return new ReviewDtos.RevisionView(revisionId,revision.revisionNo(),json.decode(revision.snapshotJson()),revision.contentHash(),revision.submitNote(),revision.submittedBy(),submitter,revision.submittedAt(),reviewView,attachmentViews);
    }

    public List<ReviewDtos.PendingReview> pending(UUID user){return reviews.pendingForReviewer(user).stream().map(value->new ReviewDtos.PendingReview(value.reviewId(),value.recordId(),value.recordCode(),value.recordTitle(),value.projectId(),value.projectName(),value.revisionNo(),value.assignedAt())).toList();}

    private Map<String,Object> snapshot(RecordStore.RecordData record,String fieldValuesJson){Map<String,Object> m=new LinkedHashMap<>();m.put("id",record.id().toString());m.put("code",record.code());m.put("projectId",record.projectId().toString());m.put("creatorId",record.creatorId().toString());m.put("title",record.title());m.put("experimentType",record.experimentType());m.put("experimentDate",record.experimentDate().toString());m.put("purpose",record.purpose());m.put("templateSnapshot",json.decode(record.templateSnapshotJson()));m.put("fieldValues",json.decode(fieldValuesJson));m.put("contentJson",json.decode(record.contentJson()));m.put("contentHtml",record.contentHtmlSanitized());m.put("contentPlainText",record.contentPlainText());return m;}
    private Map<String,Object> validateRequiredAndFiles(RecordStore.RecordData record){Map<String,Object> snapshot=json.decodeMap(record.templateSnapshotJson()),values=json.decodeMap(record.fieldValuesJson());Object fields=snapshot.get("fields");if(!(fields instanceof List<?> list))return values;Map<String,String> errors=new LinkedHashMap<>();for(Object o:list){if(!(o instanceof Map<?,?> f))continue;String key=Objects.toString(f.get("fieldKey"),""),label=Objects.toString(f.get("label"),key),type=Objects.toString(f.get("fieldType"),"");Object value=values.get(key);if("FILE".equals(type)&&value!=null){Collection<?> ids=value instanceof Collection<?> c?c:List.of(value);List<String> active=new ArrayList<>();for(Object id:ids){if(id==null)continue;UUID uuid;try{uuid=UUID.fromString(id.toString());}catch(IllegalArgumentException ignored){continue;}if(attachments.existsActive(uuid,record.id()))active.add(uuid.toString());}if(active.isEmpty())values.remove(key);else values.put(key,active);value=values.get(key);}boolean empty=value==null||value.toString().isBlank()||(value instanceof Collection<?> c&&c.isEmpty());if(Boolean.TRUE.equals(f.get("required"))&&empty)errors.put(key,label+"不能为空");}if(!errors.isEmpty())throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","请补全必填字段",errors);return values;}
    private void validateReviewer(RecordStore.RecordData record,UUID reviewer){if(record.creatorId().equals(reviewer))throw new ApiException(HttpStatus.BAD_REQUEST,"SELF_REVIEW_NOT_ALLOWED","不能审核自己创建的记录");String role=members.findRole(record.projectId(),reviewer).orElse(null);if(role==null||!Set.of("OWNER","REVIEWER").contains(role))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_REVIEWER","审核人必须是项目负责人或审核者");}
    private RecordStore.RecordData record(UUID id,boolean lock){return (lock?records.findActiveForUpdate(id):records.findActive(id)).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","记录不存在"));}
    private String projectStatus(UUID projectId){return projects.findById(projectId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","项目不存在")).status();}
    private void requireMember(UUID user,RecordStore.RecordData record){if(!members.exists(record.projectId(),user))throw new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","记录不存在或无权访问");}
    private void requireCreatorAndActive(UUID user,RecordStore.RecordData record){String role=members.findRole(record.projectId(),user).orElse(null);Decision decision=actionPolicy.canSubmit(new RecordContext(record.id(),record.projectId(),record.creatorId(),record.status(),projectStatus(record.projectId()),false,record.provisional(),role),user);if(!decision.allowed()){HttpStatus status="RESOURCE_NOT_FOUND".equals(decision.errorCode())?HttpStatus.NOT_FOUND:"ACCESS_DENIED".equals(decision.errorCode())?HttpStatus.FORBIDDEN:HttpStatus.CONFLICT;throw new ApiException(status,decision.errorCode(),decision.message());}}
    private String sha256(String value){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private String trim(String v){return v==null||v.isBlank()?null:v.trim();}
    private String normalizeKey(String v){String key=trim(v);if(key==null)return null;if(key.length()>160)throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Idempotency-Key 过长");return key;}
    private void conflict(String message){throw new ApiException(HttpStatus.CONFLICT,"REVIEW_STATE_CONFLICT",message);}
}
