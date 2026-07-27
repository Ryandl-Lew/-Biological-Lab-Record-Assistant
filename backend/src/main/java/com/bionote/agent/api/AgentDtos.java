package com.bionote.agent.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class AgentDtos {
    private AgentDtos(){}
    public record CreateRunRequest(@Schema(allowableValues={"RECORD_SUMMARY","PROJECT_PROGRESS"}) String artifactKind,Instant periodStart,Instant periodEnd,@Size(max=500) String focus){}
    public record RunView(UUID id,@Schema(allowableValues={"QUEUED","RUNNING","SUCCEEDED","FAILED","CANCELLED","LIMIT_EXCEEDED","INVALID_OUTPUT"}) String status,@Schema(allowableValues={"RECORD_SUMMARY","PROJECT_PROGRESS"}) String artifactKind,@Schema(allowableValues={"RECORD","PROJECT"}) String subjectType,UUID subjectId,UUID projectId,UUID recordId,UUID requestedBy,@Schema(allowableValues={"MANUAL","RERUN"}) String triggerType,String provider,String model,UUID promptVersionId,UUID parentRunId,int stepCount,int toolCallCount,long inputTokens,long outputTokens,String errorCode,String errorMessage,Instant createdAt,Instant startedAt,Instant finishedAt,UUID artifactId){}
    public record StepView(UUID id,int stepNo,String stepType,String toolName,JsonNode request,JsonNode response,String contentHash,long latencyMs,long inputTokens,long outputTokens,Instant createdAt){}
    public record ArtifactSummary(UUID id,UUID runId,String artifactKind,UUID projectId,UUID recordId,String headline,JsonNode period,String contentHash,Instant createdAt){}
    public record ArtifactView(UUID id,UUID runId,String artifactKind,UUID projectId,UUID recordId,JsonNode content,JsonNode evidence,String contentHash,Instant createdAt){}
}
