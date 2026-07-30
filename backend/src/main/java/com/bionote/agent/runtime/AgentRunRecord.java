package com.bionote.agent.runtime;

import java.time.Instant;
import java.util.UUID;

public record AgentRunRecord(
        UUID id,
        String artifactKind,
        String subjectType,
        UUID subjectId,
        UUID projectId,
        UUID recordId,
        UUID requestedBy,
        String triggerType,
        String status,
        String provider,
        String model,
        UUID promptVersionId,
        UUID parentRunId,
        String requestJson,
        String idempotencyKey,
        String payloadHash,
        String inputCursorJson,
        String limitsJson,
        int stepCount,
        int toolCallCount,
        long inputTokens,
        long outputTokens,
        Instant cancelRequestedAt,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        String errorCode,
        String errorMessage,
        long version) {}
