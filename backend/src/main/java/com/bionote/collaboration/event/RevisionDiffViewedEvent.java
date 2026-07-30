package com.bionote.collaboration.event;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record RevisionDiffViewedEvent(
        UUID eventId,
        UUID actorId,
        UUID projectId,
        UUID recordId,
        Instant occurredAt,
        UUID fromRevisionId,
        UUID toRevisionId)
        implements DomainEvent {
    public RevisionDiffViewedEvent {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(projectId);
        Objects.requireNonNull(recordId);
        Objects.requireNonNull(occurredAt);
    }

    @Override
    public String eventType() {
        return "REVISION_DIFF_VIEWED";
    }

    @Override
    public Map<String, Object> metadata() {
        return Map.of();
    }
}
