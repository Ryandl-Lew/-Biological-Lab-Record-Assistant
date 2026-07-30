package com.bionote.notification;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Append-only notification capability with database-backed deduplication. */
public interface NotificationAppender {
    void appendIfAbsent(
            UUID id,
            UUID recipientId,
            String type,
            String title,
            String body,
            Map<String, ?> payload,
            String dedupKey,
            Instant createdAt);
}
