package com.bionote.collaboration.event;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record RecordRevisionRestoredEvent(UUID eventId, UUID actorId, UUID projectId, UUID recordId,
        Instant occurredAt, UUID restoreOperationId, int revisionNo, UUID sourceRevisionId,
        long fromVersion, long toVersion, List<String> changedSections, int attachmentAdded,
        int attachmentRemoved) implements DomainEvent {
    public RecordRevisionRestoredEvent {
        Objects.requireNonNull(eventId); Objects.requireNonNull(actorId); Objects.requireNonNull(projectId);
        Objects.requireNonNull(recordId); Objects.requireNonNull(occurredAt); Objects.requireNonNull(restoreOperationId);
        Objects.requireNonNull(sourceRevisionId);
        changedSections = changedSections == null ? List.of() : List.copyOf(changedSections);
    }
    @Override public String eventType() { return "RECORD_REVISION_RESTORED"; }
    @Override public Map<String, Object> metadata() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("revisionNo", revisionNo); values.put("sourceRevisionId", sourceRevisionId);
        values.put("fromVersion", fromVersion); values.put("toVersion", toVersion);
        values.put("changedSections", changedSections); values.put("attachmentAdded", attachmentAdded);
        values.put("attachmentRemoved", attachmentRemoved); return Map.copyOf(values);
    }
}
