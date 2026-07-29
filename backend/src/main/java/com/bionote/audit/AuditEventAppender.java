package com.bionote.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Append-only audit persistence capability. */
public interface AuditEventAppender {
    void append(UUID eventId,UUID actorId,UUID projectId,UUID recordId,String eventType,
                String targetType,UUID targetId,Map<String,?> metadata,Instant occurredAt);
    void appendOnce(UUID eventId,UUID actorId,UUID projectId,UUID recordId,String eventType,
                    String targetType,UUID targetId,Map<String,?> metadata,Instant occurredAt);
}
