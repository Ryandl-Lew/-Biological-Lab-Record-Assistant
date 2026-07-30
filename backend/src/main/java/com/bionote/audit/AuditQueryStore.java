package com.bionote.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditQueryStore {
    boolean exists(UUID eventId, UUID projectId);

    EventPage findEvents(
            UUID projectId,
            String eventType,
            UUID actorId,
            Instant fromInclusive,
            Instant toExclusive,
            int page,
            int size);

    AttachmentPage findAttachments(UUID projectId, int page, int size);

    record EventRecord(
            UUID id,
            String eventType,
            String targetType,
            UUID targetId,
            UUID recordId,
            UUID actorId,
            String actorName,
            String metadataJson,
            Instant createdAt) {}

    record AttachmentRecord(
            UUID id,
            String filename,
            String mediaType,
            long sizeBytes,
            UUID recordId,
            String recordTitle,
            String recordCode,
            String uploaderName,
            Instant createdAt) {}

    record EventPage(List<EventRecord> items, long total) {}

    record AttachmentPage(List<AttachmentRecord> items, long total) {}
}
