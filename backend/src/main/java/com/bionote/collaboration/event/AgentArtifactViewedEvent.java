package com.bionote.collaboration.event;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record AgentArtifactViewedEvent(
        UUID eventId,
        UUID actorId,
        UUID projectId,
        UUID recordId,
        Instant occurredAt,
        UUID runId,
        String artifactKind,
        UUID artifactId)
        implements DomainEvent {
    public AgentArtifactViewedEvent {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(projectId);
        Objects.requireNonNull(occurredAt);
        Objects.requireNonNull(runId);
        Objects.requireNonNull(artifactKind);
        Objects.requireNonNull(artifactId);
    }

    @Override
    public String eventType() {
        return "AGENT_ARTIFACT_VIEWED";
    }

    @Override
    public Map<String, Object> metadata() {
        return Map.of("runId", runId, "artifactKind", artifactKind, "artifactId", artifactId);
    }
}
