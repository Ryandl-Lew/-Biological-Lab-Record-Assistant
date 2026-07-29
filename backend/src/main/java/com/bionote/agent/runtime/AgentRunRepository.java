package com.bionote.agent.runtime;

import com.bionote.agent.artifact.AgentArtifactReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** Compatibility facade retained for existing runtime tests; production services use the narrower ports. */
@Component
public class AgentRunRepository {
    private final AgentRunStore runs;
    private final AgentRunLifecycleService lifecycle;
    private final AgentArtifactReader artifacts;
    private final ObjectMapper json;
    public AgentRunRepository(AgentRunStore runs,AgentRunLifecycleService lifecycle,AgentArtifactReader artifacts,ObjectMapper json) {
        this.runs=runs;this.lifecycle=lifecycle;this.artifacts=artifacts;this.json=json;
    }
    public AgentRunRecord enqueue(UUID id,String artifactKind,String subjectType,UUID subjectId,UUID projectId,
                                  UUID recordId,UUID requestedBy,String triggerType,String provider,String model,
                                  UUID promptVersionId,UUID parentRunId,String key,String requestJson,String payloadHash,
                                  String cursorJson,String limitsJson) {
        return runs.enqueue(new AgentRunStore.NewRun(id,artifactKind,subjectType,subjectId,projectId,recordId,requestedBy,
                triggerType,provider,model,promptVersionId,parentRunId,key,requestJson,payloadHash,cursorJson,limitsJson,Instant.now()));
    }
    public AgentRunRecord findByRequesterKey(UUID requester,String key) { return runs.findByRequesterKey(requester,key); }
    public AgentRunRecord load(UUID id) { return runs.load(id); }
    public AgentRunRecord claimNext() { return runs.claimNext(); }
    public boolean cancelRequested(UUID id) { return runs.cancelRequested(id); }
    public String requestCancel(UUID id) { return lifecycle.requestCancel(id); }
    public void incrementToolCalls(UUID id,int count) { runs.incrementToolCalls(id,count); }
    public void finish(UUID id,String status,String code,String message) { lifecycle.finish(id,status,code,message); }
    public UUID completeWithArtifact(UUID runId,JsonNode content,JsonNode evidence) { return lifecycle.completeWithArtifact(runId,content,evidence); }
    public int failStaleRunning(Instant cutoff) { return lifecycle.failStaleRunning(cutoff); }
    public JsonNode artifact(UUID runId) {
        String content=artifacts.findByRunId(runId).orElseThrow(() -> new IllegalStateException("Artifact not found: "+runId)).contentJson();
        try{return json.readTree(content);}catch(Exception e){throw new IllegalStateException(e);}
    }
}
