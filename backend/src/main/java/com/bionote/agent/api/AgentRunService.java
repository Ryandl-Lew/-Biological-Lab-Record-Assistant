package com.bionote.agent.api;

import com.bionote.agent.artifact.AgentArtifactReader;
import com.bionote.agent.config.AgentCredentialService;
import com.bionote.agent.config.AgentCredentials;
import com.bionote.agent.config.AgentProperties;
import com.bionote.agent.prompt.PromptVersion;
import com.bionote.agent.prompt.PromptVersionService;
import com.bionote.agent.runtime.AgentRunLifecycleService;
import com.bionote.agent.runtime.AgentRunRecord;
import com.bionote.agent.runtime.AgentRunStore;
import com.bionote.agent.trace.AgentStepData;
import com.bionote.agent.trace.AgentStepReader;
import com.bionote.collaboration.event.AgentArtifactViewedEvent;
import com.bionote.collaboration.event.AgentRunRequestedEvent;
import com.bionote.collaboration.event.DomainEventPublisher;
import com.bionote.common.ApiException;
import com.bionote.common.PagedResponse;
import com.bionote.project.ProjectMemberStore;
import com.bionote.project.ProjectStore;
import com.bionote.record.RecordStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AgentRunService implements AgentRunUseCase {
    private static final Logger log=LoggerFactory.getLogger(AgentRunService.class);
    private final AgentProperties properties;
    private final AgentCredentialService credentials;
    private final AgentRunStore runs;
    private final AgentRunLifecycleService lifecycle;
    private final PromptVersionService prompts;
    private final AgentStepReader steps;
    private final AgentArtifactReader artifactReader;
    private final ProjectStore projects;
    private final ProjectMemberStore members;
    private final RecordStore records;
    private final ObjectMapper json;
    private final DomainEventPublisher events;

    public AgentRunService(AgentProperties properties,AgentCredentialService credentials,AgentRunStore runs,AgentRunLifecycleService lifecycle,
                           PromptVersionService prompts,AgentStepReader steps,AgentArtifactReader artifactReader,
                           ProjectStore projects,ProjectMemberStore members,RecordStore records,ObjectMapper json,
                           DomainEventPublisher events) {
        this.properties=properties;this.credentials=credentials;this.runs=runs;this.lifecycle=lifecycle;this.prompts=prompts;this.steps=steps;
        this.artifactReader=artifactReader;this.projects=projects;this.members=members;this.records=records;
        this.json=json;this.events=events;
    }

    @Override @Transactional
    public AgentDtos.RunView createRecord(UUID actor,UUID recordId,AgentDtos.CreateRunRequest request,String key) {
        requireEnabled();RecordStore.RecordData record=record(recordId,actor);
        if(!"RECORD_SUMMARY".equals(request.artifactKind()))throw invalid("artifactKind must be RECORD_SUMMARY");
        return create(actor,"RECORD_SUMMARY","RECORD",recordId,record.projectId(),recordId,record.createdAt(),
                Instant.now(),request.focus(),key,null,"MANUAL");
    }

    @Override @Transactional
    public AgentDtos.RunView createProject(UUID actor,UUID projectId,AgentDtos.CreateRunRequest request,String key) {
        requireEnabled();member(projectId,actor);
        if(!"PROJECT_PROGRESS".equals(request.artifactKind()))throw invalid("artifactKind must be PROJECT_PROGRESS");
        Instant end=request.periodEnd()==null?Instant.now():request.periodEnd();
        Instant start=request.periodStart()==null?end.minus(Duration.ofDays(7)):request.periodStart();
        if(start.isAfter(end)||Duration.between(start,end).compareTo(Duration.ofDays(90))>0)
            throw invalid("Report period must be ordered and no longer than 90 days");
        return create(actor,"PROJECT_PROGRESS","PROJECT",projectId,projectId,null,start,end,request.focus(),key,null,"MANUAL");
    }

    @Override public AgentDtos.RunView get(UUID actor,UUID runId) { return view(authorizedRun(actor,runId,false)); }

    @Override public PagedResponse<AgentDtos.StepView> steps(UUID actor,UUID runId,int page,int size) {
        AgentRunRecord run=authorizedRun(actor,runId,true);int p=Math.max(0,page),s=Math.max(1,Math.min(100,size));
        List<AgentStepData> all=steps.list(run.id());
        List<AgentDtos.StepView> data=all.stream().skip((long)p*s).limit(s).map(value -> new AgentDtos.StepView(
                value.id(),value.stepNo(),value.stepType(),value.toolName(),value.request(),value.response(),
                value.contentHash(),value.latencyMs(),value.inputTokens(),value.outputTokens(),value.createdAt())).toList();
        return PagedResponse.of(data,p,s,all.size());
    }

    @Override @Transactional
    public AgentDtos.RunView cancel(UUID actor,UUID runId) {
        AgentRunRecord run=authorizedRun(actor,runId,false);String role=member(run.projectId(),actor);
        if(!run.requestedBy().equals(actor)&&!"OWNER".equals(role))throw new ApiException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Only the requester or project owner can cancel this run");
        if(terminal(run.status()))throw new ApiException(HttpStatus.CONFLICT,"AGENT_RUN_NOT_CANCELLABLE","Agent run is already terminal");
        lifecycle.requestCancel(runId);return view(runs.load(runId));
    }

    @Override @Transactional
    public AgentDtos.RunView rerun(UUID actor,UUID runId,String key) {
        AgentRunRecord old=authorizedRun(actor,runId,false);JsonNode request=read(old.requestJson());
        AgentDtos.CreateRunRequest value=new AgentDtos.CreateRunRequest(old.artifactKind(),instant(request.path("periodStart").asText(null)),
                instant(request.path("periodEnd").asText(null)),request.path("focus").asText(null));
        if("RECORD".equals(old.subjectType())) {
            RecordStore.RecordData record=record(old.recordId(),actor);
            return create(actor,old.artifactKind(),old.subjectType(),old.subjectId(),old.projectId(),old.recordId(),
                    value.periodStart(),value.periodEnd(),value.focus(),key,old.id(),"RERUN");
        }
        member(old.projectId(),actor);
        return create(actor,old.artifactKind(),old.subjectType(),old.subjectId(),old.projectId(),null,
                value.periodStart(),value.periodEnd(),value.focus(),key,old.id(),"RERUN");
    }

    @Override public PagedResponse<AgentDtos.ArtifactSummary> recordArtifacts(UUID actor,UUID recordId,int page,int size) {
        RecordStore.RecordData record=record(recordId,actor);return artifacts(record.projectId(),recordId,page,size);
    }
    @Override public PagedResponse<AgentDtos.ArtifactSummary> projectArtifacts(UUID actor,UUID projectId,int page,int size) {
        member(projectId,actor);return artifacts(projectId,null,page,size);
    }
    @Override public AgentDtos.ArtifactView artifact(UUID actor,UUID id) {
        var artifact=artifactReader.findById(id).orElseThrow(this::hidden);member(artifact.projectId(),actor);
        events.publish(new AgentArtifactViewedEvent(UUID.randomUUID(),actor,artifact.projectId(),artifact.recordId(),
                Instant.now(),artifact.runId(),artifact.artifactKind(),id));
        return artifactView(artifact);
    }

    private AgentDtos.RunView create(UUID actor,String kind,String subjectType,UUID subjectId,UUID projectId,
                                     UUID recordId,Instant start,Instant end,String focus,String key,UUID parent,String trigger) {
        String idempotencyKey=key(key),trimmed=focus==null?"":focus.trim();
        if(trimmed.length()>500)throw invalid("focus exceeds 500 characters");
        Map<String,Object> request=new LinkedHashMap<>();request.put("artifactKind",kind);request.put("periodStart",start);
        request.put("periodEnd",end);request.put("focus",trimmed);
        String requestJson=write(request);
        String payload=sha("RECORD_SUMMARY".equals(kind)?kind+"|"+subjectId+"|"+trimmed:kind+"|"+subjectId+"|"+start+"|"+end+"|"+trimmed);
        AgentRunRecord existing=runs.findByRequesterKey(actor,idempotencyKey);
        if(existing!=null) {
            if(existing.payloadHash().equals(payload))return view(existing);
            throw new ApiException(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","Idempotency key was used for a different agent request");
        }
        rate(actor,projectId,subjectId,kind,payload);PromptVersion prompt=prompts.active(promptName(kind));
        ProjectStore.ProjectRecord project=projects.findById(projectId).orElseThrow(this::hidden);
        RecordStore.RecordData record=recordId==null?null:records.findActive(recordId).orElseThrow(this::hidden);
        Instant createdAt=Instant.now();Map<String,Object> cursor=new LinkedHashMap<>();cursor.put("periodStart",start);
        cursor.put("periodEnd",end);cursor.put("createdAt",createdAt);cursor.put("projectVersion",project.version());
        cursor.put("maxRevisionNo",record==null?Integer.MAX_VALUE:record.currentRevisionNo());
        if(record!=null)cursor.put("recordVersion",record.version());
        String limits=write(Map.of("maxSteps",properties.getMaxSteps(),"maxToolCalls",properties.getMaxToolCalls(),
                "maxModelCalls",properties.getMaxModelCalls(),"maxDurationMs",properties.getMaxDurationMs(),
                "maxOutputTokens",properties.getMaxOutputTokens(),"maxRepairTurns",properties.getMaxRepairTurns()));
        AgentCredentials activeCredentials=credentials.resolve(actor);
        UUID id=UUID.randomUUID();AgentRunRecord run=runs.enqueue(new AgentRunStore.NewRun(id,kind,subjectType,subjectId,
                projectId,recordId,actor,trigger,activeCredentials.provider(),activeCredentials.model(),prompt.id(),parent,
                idempotencyKey,requestJson,payload,write(cursor),limits,createdAt));
        events.publish(new AgentRunRequestedEvent(UUID.randomUUID(),actor,projectId,recordId,Instant.now(),id,kind,trigger,"QUEUED"));
        return view(run);
    }

    private void rate(UUID actor,UUID projectId,UUID subjectId,String kind,String payload) {
        if(runs.countActiveForRequester(actor)>=properties.getMaxConcurrentPerUser()
                ||runs.countActiveForProject(projectId)>=properties.getMaxConcurrentPerProject())throw limited();
        Instant cutoff=Instant.now().minusSeconds(Math.max(0,properties.getSameSubjectCooldownSeconds()));
        if(runs.countRecentMatching(subjectId,kind,payload,cutoff)>0)throw limited();
    }
    private AgentRunRecord authorizedRun(UUID actor,UUID id,boolean trace) {
        AgentRunRecord run;try{run=runs.load(id);}catch(Exception e){throw hidden();}
        String role=member(run.projectId(),actor);if(trace&&!run.requestedBy().equals(actor)&&!"OWNER".equals(role))throw hidden();return run;
    }
    private RecordStore.RecordData record(UUID id,UUID actor) {
        RecordStore.RecordData record=records.findActive(id).filter(value -> !value.provisional()).orElseThrow(this::hidden);
        if(members.findRole(record.projectId(),actor).isEmpty())throw hidden();return record;
    }
    private String member(UUID projectId,UUID actor) { return members.findRole(projectId,actor).orElseThrow(this::hidden); }
    private PagedResponse<AgentDtos.ArtifactSummary> artifacts(UUID projectId,UUID recordId,int page,int size) {
        int p=Math.max(0,page),s=Math.max(1,Math.min(100,size));
        AgentArtifactReader.PageSlice slice=recordId==null?artifactReader.findByProject(projectId,p,s):artifactReader.findByRecord(recordId,p,s);
        return PagedResponse.of(slice.items().stream().map(this::artifactSummary).toList(),p,s,slice.total());
    }
    private AgentDtos.RunView view(AgentRunRecord run) {
        UUID artifactId=artifactReader.findByRunId(run.id()).map(AgentArtifactReader.ArtifactRecord::id).orElse(null);
        return new AgentDtos.RunView(run.id(),run.status(),run.artifactKind(),run.subjectType(),run.subjectId(),
                run.projectId(),run.recordId(),run.requestedBy(),run.triggerType(),run.provider(),run.model(),
                run.promptVersionId(),run.parentRunId(),run.stepCount(),run.toolCallCount(),run.inputTokens(),
                run.outputTokens(),run.errorCode(),run.errorMessage(),run.createdAt(),run.startedAt(),run.finishedAt(),artifactId);
    }
    private AgentDtos.ArtifactSummary artifactSummary(AgentArtifactReader.ArtifactRecord value) {
        JsonNode content=read(value.contentJson());return new AgentDtos.ArtifactSummary(value.id(),value.runId(),
                value.artifactKind(),value.projectId(),value.recordId(),content.path("headline").asText(),
                content.path("period"),value.contentHash(),value.createdAt());
    }
    private AgentDtos.ArtifactView artifactView(AgentArtifactReader.ArtifactRecord value) {
        return new AgentDtos.ArtifactView(value.id(),value.runId(),value.artifactKind(),value.projectId(),value.recordId(),
                read(value.contentJson()),read(value.evidenceJson()),value.contentHash(),value.createdAt());
    }
    private void requireEnabled(){if(!properties.isEnabled()){log.warn("Agent run request rejected: agent.enabled=false (set AGENT_ENABLED=true to enable)");throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"AGENT_DISABLED","Agent functionality is disabled");}}
    private String promptName(String kind){return "RECORD_SUMMARY".equals(kind)?"record-summary":"project-progress";}
    private String key(String value){if(value==null||value.isBlank()||value.trim().length()>160)throw invalid("A valid Idempotency-Key is required");return value.trim();}
    private boolean terminal(String status){return List.of("SUCCEEDED","FAILED","CANCELLED","LIMIT_EXCEEDED","INVALID_OUTPUT").contains(status);}
    private ApiException invalid(String message){return new ApiException(HttpStatus.BAD_REQUEST,"AGENT_INVALID_REQUEST",message);}
    private ApiException hidden(){return new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","Resource not found or inaccessible");}
    private ApiException limited(){return new ApiException(HttpStatus.TOO_MANY_REQUESTS,"AGENT_RATE_LIMITED","Agent run rate limit exceeded");}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException(e);}}
    private JsonNode read(String value){try{return json.readTree(value);}catch(Exception e){return json.createObjectNode();}}
    private String sha(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private Instant instant(String value){try{return value==null?null:Instant.parse(value);}catch(Exception e){return null;}}
}
