package com.bionote.agent.infrastructure.persistence;

import com.bionote.agent.runtime.AgentRunRecord;
import com.bionote.agent.runtime.AgentRunStore;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class JpaAgentRunStore implements AgentRunStore {
    private final AgentRunJpaRepository runs;

    public JpaAgentRunStore(AgentRunJpaRepository runs) {
        this.runs = runs;
    }

    @Override
    public AgentRunRecord enqueue(NewRun run) {
        var entity =
                new AgentRunEntity(
                        run.id(),
                        run.artifactKind(),
                        run.subjectType(),
                        run.subjectId(),
                        run.projectId(),
                        run.recordId(),
                        run.requestedBy(),
                        run.triggerType(),
                        run.provider(),
                        run.model(),
                        run.promptVersionId(),
                        run.parentRunId(),
                        run.idempotencyKey(),
                        run.requestJson(),
                        run.payloadHash(),
                        run.inputCursorJson(),
                        run.limitsJson(),
                        run.createdAt());
        return map(runs.saveAndFlush(entity));
    }

    @Override
    public AgentRunRecord findByRequesterKey(UUID requesterId, String idempotencyKey) {
        return runs.findByRequestedByAndIdempotencyKey(requesterId, idempotencyKey)
                .map(this::map)
                .orElse(null);
    }

    @Override
    public AgentRunRecord load(UUID runId) {
        return runs.findById(runId)
                .map(this::map)
                .orElseThrow(() -> new IllegalStateException("Agent run not found: " + runId));
    }

    @Override
    public AgentRunRecord loadForUpdate(UUID runId) {
        return runs.findByIdForUpdate(runId)
                .map(this::map)
                .orElseThrow(() -> new IllegalStateException("Agent run not found: " + runId));
    }

    @Override
    @Transactional
    public AgentRunRecord claimNext() {
        for (var candidate : runs.findClaimCandidates()) {
            Instant now = Instant.now();
            if (runs.claim(candidate.getId(), candidate.getVersion(), now) == 1)
                return load(UUID.fromString(candidate.getId()));
        }
        return null;
    }

    @Override
    public boolean cancelRequested(UUID runId) {
        return load(runId).cancelRequestedAt() != null;
    }

    @Override
    public int cancelQueued(UUID runId, Instant now) {
        return runs.cancelQueued(runId.toString(), now);
    }

    @Override
    public int requestCancelRunning(UUID runId, Instant now) {
        return runs.requestCancelRunning(runId.toString(), now);
    }

    @Override
    public void incrementToolCalls(UUID runId, int count) {
        runs.incrementToolCalls(runId.toString(), count);
    }

    @Override
    public int finishRunning(
            UUID runId, String status, String errorCode, String errorMessage, Instant now) {
        return runs.finishRunning(runId.toString(), status, errorCode, errorMessage, now);
    }

    @Override
    public int markSucceededIfRunningAndNotCancelled(UUID runId, Instant now) {
        return runs.markSucceeded(runId.toString(), now);
    }

    @Override
    public int failStaleRunning(Instant cutoff, Instant now) {
        return runs.failStaleRunning(cutoff, now);
    }

    @Override
    public long countActiveForRequester(UUID requesterId) {
        return runs.countActiveForRequester(requesterId);
    }

    @Override
    public long countActiveForProject(UUID projectId) {
        return runs.countActiveForProject(projectId);
    }

    @Override
    public long countRecentMatching(
            UUID subjectId, String artifactKind, String payloadHash, Instant cutoff) {
        return runs.countBySubjectIdAndArtifactKindAndPayloadHashAndCreatedAtGreaterThanEqual(
                subjectId, artifactKind, payloadHash, cutoff);
    }

    private AgentRunRecord map(AgentRunEntity r) {
        return new AgentRunRecord(
                r.id,
                r.artifactKind,
                r.subjectType,
                r.subjectId,
                r.projectId,
                r.recordId,
                r.requestedBy,
                r.triggerType,
                r.status,
                r.provider,
                r.model,
                r.promptVersionId,
                r.parentRunId,
                r.requestJson,
                r.idempotencyKey,
                r.payloadHash,
                r.inputCursorJson,
                r.limitsJson,
                r.stepCount,
                r.toolCallCount,
                r.inputTokens,
                r.outputTokens,
                r.cancelRequestedAt,
                r.createdAt,
                r.startedAt,
                r.finishedAt,
                r.errorCode,
                r.errorMessage,
                r.version);
    }
}
