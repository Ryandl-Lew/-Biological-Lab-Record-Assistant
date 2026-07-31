package com.bionote.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class AuditDtos {
    private AuditDtos() {}

    public record View(
            UUID id,
            String eventType,
            String targetType,
            UUID targetId,
            UUID recordId,
            UUID actorId,
            String actorName,
            Map<String, Object> metadata,
            Instant createdAt) {}

    public record AttachmentSummary(
            UUID id,
            String filename,
            String mediaType,
            long sizeBytes,
            UUID recordId,
            String recordTitle,
            String recordCode,
            String uploaderName,
            Instant createdAt) {}
}
