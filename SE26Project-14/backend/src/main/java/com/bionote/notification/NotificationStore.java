package com.bionote.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationStore {
    PageSlice findByRecipient(UUID recipientId, boolean unreadOnly, int page, int size);

    long countUnread(UUID recipientId);

    boolean markRead(UUID id, UUID recipientId, Instant readAt);

    void markAllRead(UUID recipientId, Instant readAt);

    record NotificationRecord(
            UUID id,
            UUID recipientId,
            String type,
            String title,
            String body,
            String payloadJson,
            String dedupKey,
            Instant createdAt,
            Instant readAt) {}

    record PageSlice(List<NotificationRecord> items, long total) {}
}
