package com.bionote.collaboration.event;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record AgentRunFailedEvent(
        UUID eventId,
        UUID actorId,
        UUID projectId,
        UUID recordId,
        Instant occurredAt,
        UUID runId,
        String artifactKind,
        String triggerType,
        String status,
        String errorCode)
        implements DomainEvent {
    public AgentRunFailedEvent {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(projectId);
        Objects.requireNonNull(occurredAt);
        Objects.requireNonNull(runId);
        Objects.requireNonNull(artifactKind);
        Objects.requireNonNull(triggerType);
        Objects.requireNonNull(status);
        Objects.requireNonNull(errorCode);
    }

    @Override
    public String eventType() {
        return "AGENT_RUN_FAILED";
    }

    @Override
    public Map<String, Object> metadata() {
        return Map.of(
                "runId",
                runId,
                "artifactKind",
                artifactKind,
                "triggerType",
                triggerType,
                "status",
                status,
                "errorCode",
                errorCode);
    }
}
