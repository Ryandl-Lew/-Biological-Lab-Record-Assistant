package com.bionote.agent.runtime;

import com.bionote.agent.artifact.AgentArtifactAppender;
import com.bionote.collaboration.event.AgentRunSucceededEvent;
import com.bionote.collaboration.event.DomainEventPublisher;
import com.bionote.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AgentRunLifecycleService {
    private final AgentRunStore runs;
    private final AgentArtifactAppender artifacts;
    private final AgentRunStateMachine states;
    private final DomainEventPublisher events;
    private final ObjectMapper json;
    public AgentRunLifecycleService(AgentRunStore runs,AgentArtifactAppender artifacts,AgentRunStateMachine states,
                                    DomainEventPublisher events,ObjectMapper json) {
        this.runs=runs;this.artifacts=artifacts;this.states=states;this.events=events;this.json=json;
    }

    @Transactional
    public String requestCancel(UUID runId) {
        AgentRunRecord run=runs.load(runId);
        if(states.terminal(run.status()))return run.status();
        Instant now=Instant.now();
        if("QUEUED".equals(run.status())) {
            states.require("QUEUED","CANCELLED");
            runs.cancelQueued(runId,now);
            return "CANCELLED";
        }
        runs.requestCancelRunning(runId,now);
        return "RUNNING";
    }

    @Transactional
    public void finish(UUID runId,String status,String code,String message) {
        states.require("RUNNING",status);
        int changed=runs.finishRunning(runId,status,code,limit(message,1000),Instant.now());
        if(changed!=1)throw new IllegalStateException("Agent run is no longer RUNNING: "+runId);
    }

    @Transactional
    public UUID completeWithArtifact(UUID runId,JsonNode content,JsonNode evidence) {
        AgentRunRecord run=runs.loadForUpdate(runId);
        states.require(run.status(),"SUCCEEDED");
        if(run.cancelRequestedAt()!=null)throw cancelled();
        UUID artifactId=UUID.randomUUID();
        String contentJson=encode(content),evidenceJson=encode(evidence);
        Instant now=Instant.now();
        artifacts.append(new AgentArtifactAppender.ArtifactRecord(artifactId,runId,run.artifactKind(),run.projectId(),
                run.recordId(),contentJson,evidenceJson,hash(contentJson),now));
        if(runs.markSucceededIfRunningAndNotCancelled(runId,now)!=1)throw cancelled();
        events.publish(new AgentRunSucceededEvent(UUID.randomUUID(),run.requestedBy(),run.projectId(),run.recordId(),
                now,runId,run.artifactKind(),run.triggerType(),"SUCCEEDED",artifactId));
        return artifactId;
    }

    @Transactional
    public int failStaleRunning(Instant cutoff) { return runs.failStaleRunning(cutoff,Instant.now()); }

    private ApiException cancelled() {
        return new ApiException(HttpStatus.CONFLICT,"AGENT_RUN_CANCELLED","Agent run was cancelled before artifact persistence");
    }
    private String encode(JsonNode value) {
        try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException(e);}
    }
    private String hash(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    private String limit(String value,int max) { return value==null?null:(value.length()<=max?value:value.substring(0,max)); }
}
