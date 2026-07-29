package com.bionote.audit;

import com.bionote.common.ApiException;
import com.bionote.common.PagedResponse;
import com.bionote.project.ProjectMemberStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

@Service
public class AuditService implements AuditUseCase {
    private static final Set<String> SAFE_KEYS=Set.of("name","title","code","email","role","from","to","reason","revisionNo","reviewerId","inviteeUserId","filename","sizeBytes","comment","sourceRevisionId","fromVersion","toVersion","changedSections","attachmentAdded","attachmentRemoved","runId","artifactKind","triggerType","status","errorCode","artifactId");
    private static final Set<String> TIMELINE_EVENTS=Set.of("PROJECT_CREATED","PROJECT_ARCHIVED","INVITATION_CREATED","INVITATION_ACCEPTED","INVITATION_REJECTED","INVITATION_EXPIRED","MEMBER_ROLE_CHANGED","MEMBER_REMOVED","RECORD_CREATED","RECORD_DELETED","ATTACHMENT_UPLOADED","ATTACHMENT_DELETED","RECORD_SUBMITTED","REVIEW_CHANGES_REQUESTED","REVIEW_APPROVED","REVIEWER_REASSIGNED","RECORD_EXPORT_PREVIEW","RECORD_EXPORT_MARKDOWN","RECORD_EXPORT_PDF","RECORD_REVISION_RESTORED","AGENT_RUN_SUCCEEDED");
    private final AuditQueryStore queries;private final AuditJsonCodec json;private final ProjectMemberStore members;
    public AuditService(AuditQueryStore queries,AuditJsonCodec json,ProjectMemberStore members){this.queries=queries;this.json=json;this.members=members;}
    public PagedResponse<AuditDtos.View> list(UUID user,UUID project,String eventType,UUID actor,LocalDate from,LocalDate to,int page,int size){requireMember(user,project);page=Math.max(0,page);size=Math.max(1,Math.min(100,size));String ev=blank(eventType)?null:eventType.trim();if(ev!=null&&!TIMELINE_EVENTS.contains(ev))throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","不支持的时间线事件类型");Instant start=from==null?null:from.atStartOfDay(ZoneOffset.UTC).toInstant(),end=to==null?null:to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();AuditQueryStore.EventPage result=queries.findEvents(project,ev,actor,start,end,page,size);List<AuditDtos.View> data=result.items().stream().map(value->new AuditDtos.View(value.id(),value.eventType(),value.targetType(),value.targetId(),value.recordId(),value.actorId(),value.actorName(),safe(value.metadataJson()),value.createdAt())).toList();return PagedResponse.of(data,page,size,result.total());}
    public PagedResponse<AuditDtos.AttachmentSummary> attachments(UUID user,UUID project,int page,int size){requireMember(user,project);page=Math.max(0,page);size=Math.max(1,Math.min(100,size));AuditQueryStore.AttachmentPage result=queries.findAttachments(project,page,size);List<AuditDtos.AttachmentSummary> data=result.items().stream().map(value->new AuditDtos.AttachmentSummary(value.id(),value.filename(),value.mediaType(),value.sizeBytes(),value.recordId(),value.recordTitle(),value.recordCode(),value.uploaderName(),value.createdAt())).toList();return PagedResponse.of(data,page,size,result.total());}
    private void requireMember(UUID user,UUID project){if(!members.exists(project,user))throw new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","项目不存在或无权访问");}
    private Map<String,Object>safe(String value){Map<String,Object> raw=json.decode(value),safe=new LinkedHashMap<>();raw.forEach((k,v)->{if(SAFE_KEYS.contains(k))safe.put(k,v);});return safe;}
    private boolean blank(String v){return v==null||v.isBlank();}
}
