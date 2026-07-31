package com.bionote.agent.runtime;

import java.time.Instant;
import java.util.UUID;

/** Persistence port for the mutable agent run state machine. */
public interface AgentRunStore {
    AgentRunRecord enqueue(NewRun run);

    AgentRunRecord findByRequesterKey(UUID requesterId, String idempotencyKey);

    AgentRunRecord load(UUID runId);

    AgentRunRecord loadForUpdate(UUID runId);

    AgentRunRecord claimNext();

    boolean cancelRequested(UUID runId);

    int cancelQueued(UUID runId, Instant now);

    int requestCancelRunning(UUID runId, Instant now);

    void incrementToolCalls(UUID runId, int count);

    int finishRunning(
            UUID runId, String status, String errorCode, String errorMessage, Instant now);

    int markSucceededIfRunningAndNotCancelled(UUID runId, Instant now);

    int failStaleRunning(Instant cutoff, Instant now);

    long countActiveForRequester(UUID requesterId);

    long countActiveForProject(UUID projectId);

    long countRecentMatching(
            UUID subjectId, String artifactKind, String payloadHash, Instant cutoff);

    record NewRun(
            UUID id,
            String artifactKind,
            String subjectType,
            UUID subjectId,
            UUID projectId,
            UUID recordId,
            UUID requestedBy,
            String triggerType,
            String provider,
            String model,
            UUID promptVersionId,
            UUID parentRunId,
            String idempotencyKey,
            String requestJson,
            String payloadHash,
            String inputCursorJson,
            String limitsJson,
            Instant createdAt) {}
}
