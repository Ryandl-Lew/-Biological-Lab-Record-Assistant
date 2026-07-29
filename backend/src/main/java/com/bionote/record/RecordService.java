package com.bionote.record;

import com.bionote.attachment.AttachmentStorage;
import com.bionote.attachment.AttachmentStore;
import com.bionote.collaboration.CollaborationEvents;
import com.bionote.common.ApiException;
import com.bionote.common.PagedResponse;
import com.bionote.domain.record.Decision;
import com.bionote.domain.record.RecordActionPolicy;
import com.bionote.domain.record.RecordContext;
import com.bionote.project.ProjectMemberStore;
import com.bionote.project.ProjectStore;
import com.bionote.template.TemplateDtos;
import com.bionote.template.TemplateUseCase;
import com.bionote.user.UserRepository;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class RecordService implements RecordUseCase {
    private final RecordStore records;
    private final AttachmentStore attachments;
    private final RecordRevisionDisplayReader revisions;
    private final RecordJsonCodec json;
    private final TemplateUseCase templates;
    private final ProjectStore projects;
    private final ProjectMemberStore members;
    private final UserRepository users;
    private final CollaborationEvents events;
    private final AttachmentStorage storage;
    private final RecordActionPolicy actionPolicy;

    public RecordService(RecordStore records, AttachmentStore attachments, RecordRevisionDisplayReader revisions,
                         RecordJsonCodec json, TemplateUseCase templates, ProjectStore projects,
                         ProjectMemberStore members, UserRepository users, CollaborationEvents events,
                         AttachmentStorage storage, RecordActionPolicy actionPolicy) {
        this.records=records;this.attachments=attachments;this.revisions=revisions;this.json=json;this.templates=templates;
        this.projects=projects;this.members=members;this.users=users;this.events=events;this.storage=storage;this.actionPolicy=actionPolicy;
    }

    @Override @Transactional public RecordDtos.View create(UUID user, RecordDtos.CreateRequest request) {
        String role=requireActiveProject(request.projectId(),user);
        if("REVIEWER".equals(role))throw new ApiException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","审核者不能创建记录");
        Object snapshot=request.templateId()==null?blankSnapshot():templates.get(user,request.templateId());
        Map<String,Object> defaults=defaults(snapshot);
        UUID id=UUID.randomUUID();String code=code(id);Instant now=Instant.now();
        records.insert(new RecordStore.NewRecord(id,code,request.projectId(),user,request.title().trim(),
                request.experimentType().trim(),request.experimentDate(),request.purpose().trim(),false,
                json.encode(snapshot),json.encode(defaults),"{}","","",now));
        events.audit(user,request.projectId(),id,"RECORD_CREATED","RECORD",id,Map.of("title",request.title().trim(),"code",code));
        return get(user,id);
    }

    @Override @Transactional public RecordDtos.View reserve(UUID user, RecordDtos.ReserveRequest request) {
        String role=requireActiveProject(request.projectId(),user);
        if("REVIEWER".equals(role))throw new ApiException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","审核者不能创建记录");
        Object snapshot=request.templateId()==null?blankSnapshot():templates.get(user,request.templateId());
        Map<String,Object> defaults=defaults(snapshot);
        String type=snapshot instanceof TemplateDtos.View view?Objects.toString(view.experimentType(),""):"";
        UUID id=UUID.randomUUID();String code=code(id);Instant now=Instant.now();
        records.insert(new RecordStore.NewRecord(id,code,request.projectId(),user,"未命名记录",
                type.isBlank()?"未分类":type,LocalDate.now(ZoneOffset.UTC),"",true,json.encode(snapshot),
                json.encode(defaults),"{}","","",now));
        return get(user,id);
    }

    @Override @Transactional public RecordDtos.View update(UUID user, UUID id, RecordDtos.UpdateRequest request) {
        RecordStore.RecordData record=record(id);requireMember(record.projectId(),user);requireEditable(record,user);
        if(record.version()!=request.version())throw optimisticConflict();
        Map<String,Object> normalized=normalizeFieldValues(id,record.templateSnapshotJson(),request.fieldValues());
        String title=request.title().trim(),type=request.experimentType().trim(),purpose=request.purpose().trim();
        String fields=json.encode(normalized),content=json.encode(request.contentJson()==null?Map.of():request.contentJson());
        String clean=Jsoup.clean(request.contentHtml()==null?"":request.contentHtml(),
                Safelist.relaxed().addTags("h2","h3","blockquote","hr","pre","code","s","strike")
                        .addAttributes("a","target","rel"));
        String plain=Jsoup.parse(clean).text();
        boolean same=title.equals(record.title())&&type.equals(record.experimentType())
                &&request.experimentDate().equals(record.experimentDate())&&purpose.equals(record.purpose())
                &&fields.equals(record.fieldValuesJson())&&content.equals(record.contentJson())
                &&clean.equals(record.contentHtmlSanitized());
        if(same&&!record.provisional())return get(user,id);
        try {
            if(!records.updateWorkingCopy(id,request.version(),new RecordStore.UpdateRecord(title,type,
                    request.experimentDate(),purpose,fields,content,clean,plain,false,Instant.now())))throw optimisticConflict();
        } catch(OptimisticLockingFailureException e){throw optimisticConflict();}
        events.audit(user,record.projectId(),id,record.provisional()?"RECORD_CREATED":"RECORD_UPDATED","RECORD",id,
                record.provisional()?Map.of("title",title,"code",record.code()):Map.of("title",title));
        return get(user,id);
    }

    @Override @Transactional public void discardReservation(UUID user, UUID id) {
        RecordStore.RecordData record=record(id);requireMember(record.projectId(),user);
        if(!record.creatorId().equals(user))throw new ApiException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","只有记录创建者可以舍弃草稿");
        if(!record.provisional())throw new ApiException(HttpStatus.CONFLICT,"RECORD_STATE_CONFLICT","该记录已保存，不能按临时草稿舍弃");
        List<String> keys=attachments.storageKeysByRecord(id);
        attachments.deleteByRecord(id);
        records.deleteProvisional(id,user);
        keys.forEach(storage::deleteQuietly);
    }

    @Override public PagedResponse<RecordDtos.View> list(UUID user, UUID project, UUID creator, String status,
                                                         String keyword, int page, int size) {
        page=Math.max(0,page);size=Math.max(1,Math.min(100,size));
        String normalizedStatus=status==null||status.isBlank()?null:status;
        var result=records.searchVisible(user,project,creator,normalizedStatus,esc(keyword),page,size);
        return PagedResponse.of(result.ids().stream().map(id->get(user,id)).toList(),page,size,result.total());
    }

    @Override public RecordDtos.View get(UUID user, UUID id) {
        RecordStore.RecordData record=record(id);
        ProjectStore.ProjectRecord project=projects.findById(record.projectId())
                .orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","记录不存在"));
        String role=requireMember(record.projectId(),user);
        String creatorName=users.findById(record.creatorId()).map(com.bionote.user.User::getDisplayName).orElse("");
        RecordContext context=new RecordContext(id,record.projectId(),record.creatorId(),record.status(),project.status(),
                false,record.provisional(),role);
        boolean editable=actionPolicy.canEdit(context,user).allowed();
        boolean submittable=actionPolicy.canSubmit(context,user).allowed();
        boolean restorable=actionPolicy.canRestore(context,user).allowed();

        String title=record.title(),type=record.experimentType(),purpose=record.purpose();
        String html=record.contentHtmlSanitized(),plain=record.contentPlainText();
        LocalDate date=record.experimentDate();
        Object template=json.decode(record.templateSnapshotJson()),content=json.decode(record.contentJson()),displayed=null;
        Map<String,Object> values=json.decodeMap(record.fieldValuesJson());
        java.util.Optional<RecordRevisionDisplayReader.DisplayedRevision> revision=
                "COMPLETED".equals(record.status())?revisions.findCompleted(record.finalRevisionId()):
                        "IN_REVIEW".equals(record.status())?revisions.findInReview(record.currentReviewId()):java.util.Optional.empty();
        if(revision.isPresent()){
            var value=revision.get();Map<String,Object> snapshot=json.decodeMap(value.snapshotJson());
            title=Objects.toString(snapshot.get("title"),title);
            type=Objects.toString(snapshot.get("experimentType"),type);
            purpose=Objects.toString(snapshot.get("purpose"),purpose);
            date=LocalDate.parse(Objects.toString(snapshot.get("experimentDate"),date.toString()));
            template=snapshot.getOrDefault("templateSnapshot",template);
            if(snapshot.get("fieldValues") instanceof Map<?,?> map)values=new LinkedHashMap<>((Map<String,Object>)map);
            content=snapshot.getOrDefault("contentJson",content);
            html=Objects.toString(snapshot.get("contentHtml"),html);
            plain=Objects.toString(snapshot.get("contentPlainText"),plain);
            displayed=Map.of("id",value.id().toString(),"revisionNo",value.revisionNo());
        }
        return new RecordDtos.View(id,record.code(),record.projectId(),project.name(),record.creatorId(),creatorName,
                title,type,date,purpose,record.status(),record.provisional(),template,values,content,html,plain,
                record.currentRevisionNo(),record.currentReviewId(),displayed,record.version(),record.createdAt(),
                record.updatedAt(),Map.of("canEdit",editable,"canDelete",editable&&!record.provisional(),
                        "canSubmit",submittable,"canRestore",restorable,"canView",true,
                        "isProjectOwner","OWNER".equals(role)));
    }

    @Override @Transactional public void delete(UUID user, UUID id) {
        RecordStore.RecordData record=record(id);requireMember(record.projectId(),user);requireEditable(record,user);
        try {
            if(!records.softDelete(id,record.version(),Instant.now()))throw optimisticConflict();
        } catch(OptimisticLockingFailureException e){throw optimisticConflict();}
        events.audit(user,record.projectId(),id,"RECORD_DELETED","RECORD",id,Map.of("title",record.title()));
    }

    private Object blankSnapshot(){return Map.of("name","空白记录","version",1,"fields",List.of());}
    private Map<String,Object> defaults(Object snapshot){
        Map<String,Object> values=new LinkedHashMap<>();
        if(snapshot instanceof TemplateDtos.View view)for(var field:view.fields())if(field.defaultValue()!=null)values.put(field.fieldKey(),field.defaultValue());
        return values;
    }
    private String code(UUID id){return "EXP-"+DateTimeFormatter.BASIC_ISO_DATE.format(LocalDate.now(ZoneOffset.UTC))+"-"+id.toString().substring(0,8).toUpperCase(Locale.ROOT);}

    private Map<String,Object> normalizeFieldValues(UUID recordId,String snapshotJson,Map<String,Object> values){
        Map<String,Object> safe=new LinkedHashMap<>(values==null?Map.of():values);
        Object snapshot=json.decode(snapshotJson);if(!(snapshot instanceof Map<?,?> map))return safe;
        Object fieldObject=map.get("fields");if(!(fieldObject instanceof List<?> fields))return safe;
        for(Object item:fields){
            if(!(item instanceof Map<?,?> field))continue;
            String key=Objects.toString(field.get("fieldKey"),"");
            String type=Objects.toString(field.get("fieldType"),"");
            Object value=safe.get(key);if(value==null||value.toString().isBlank())continue;
            if("FILE".equals(type)){
                Collection<?> ids=value instanceof Collection<?> collection?collection:value instanceof String?List.of(value):null;
                if(ids==null)throw fieldTypeError(key);
                List<String> active=new ArrayList<>();
                for(Object candidate:ids){
                    if(candidate==null)continue;
                    try{
                        UUID attachment=UUID.fromString(candidate.toString());
                        if(attachments.existsActive(attachment,recordId))active.add(attachment.toString());
                    }catch(IllegalArgumentException ignored){}
                }
                if(active.isEmpty())safe.remove(key);else safe.put(key,active);
                continue;
            }
            try{
                if("NUMBER".equals(type))Double.parseDouble(value.toString());
                if("DATE".equals(type))LocalDate.parse(value.toString());
                if("SELECT".equals(type)&&field.get("options") instanceof List<?> options&&!options.contains(value))throw new IllegalArgumentException();
            }catch(Exception e){throw fieldTypeError(key);}
        }
        return safe;
    }

    private ApiException fieldTypeError(String key){
        return new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","模板字段值类型不正确",Map.of(key,"字段值类型不正确"));
    }
    private RecordStore.RecordData record(UUID id){
        return records.findActive(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","记录不存在"));
    }
    private String requireMember(UUID project,UUID user){
        return members.findRole(project,user).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","记录不存在或无权访问"));
    }
    private String requireActiveProject(UUID project,UUID user){
        String role=requireMember(project,user);
        if(!"ACTIVE".equals(projects.findById(project).map(ProjectStore.ProjectRecord::status).orElse(null)))
            throw new ApiException(HttpStatus.CONFLICT,"PROJECT_ARCHIVED","项目已归档，只能查看");
        return role;
    }
    private void requireEditable(RecordStore.RecordData record,UUID user){
        String role=requireMember(record.projectId(),user);
        String projectStatus=projects.findById(record.projectId()).map(ProjectStore.ProjectRecord::status).orElse("");
        Decision decision=actionPolicy.canEdit(new RecordContext(record.id(),record.projectId(),record.creatorId(),
                record.status(),projectStatus,false,record.provisional(),role),user);
        if(!decision.allowed())throw policyException(decision);
    }
    private ApiException policyException(Decision decision){
        HttpStatus status="RESOURCE_NOT_FOUND".equals(decision.errorCode())?HttpStatus.NOT_FOUND:
                "ACCESS_DENIED".equals(decision.errorCode())?HttpStatus.FORBIDDEN:HttpStatus.CONFLICT;
        return new ApiException(status,decision.errorCode(),decision.message());
    }
    private ApiException optimisticConflict(){return new ApiException(HttpStatus.CONFLICT,"OPTIMISTIC_LOCK_CONFLICT","记录已被其他页面更新");}
    private String esc(String value){return value==null?"":value.trim().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_");}
}
