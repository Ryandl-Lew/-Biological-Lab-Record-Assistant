package com.bionote.agent.trace;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Compatibility facade retained for existing tests; application code uses append/read ports separately. */
@Component
public class AgentStepRepository {
    private final AgentStepAppender appender;
    private final AgentStepReader reader;
    public AgentStepRepository(AgentStepAppender appender,AgentStepReader reader) { this.appender=appender;this.reader=reader; }
    public Step append(UUID runId,String type,String toolName,JsonNode request,JsonNode response,long latency,long inputTokens,long outputTokens) {
        return map(appender.append(runId,type,toolName,request,response,latency,inputTokens,outputTokens));
    }
    public List<Step> list(UUID runId) { return reader.list(runId).stream().map(this::map).toList(); }
    private Step map(AgentStepData value) {
        return new Step(value.id(),value.runId(),value.stepNo(),value.stepType(),value.toolName(),value.request(),
                value.response(),value.contentHash(),value.latencyMs(),value.inputTokens(),value.outputTokens(),value.createdAt());
    }
    public record Step(UUID id,UUID runId,int stepNo,String stepType,String toolName,JsonNode request,JsonNode response,
                       String contentHash,long latencyMs,long inputTokens,long outputTokens,Instant createdAt) {}
}
