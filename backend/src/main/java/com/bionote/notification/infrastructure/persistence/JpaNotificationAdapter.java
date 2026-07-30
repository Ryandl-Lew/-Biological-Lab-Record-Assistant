package com.bionote.notification.infrastructure.persistence;

import com.bionote.notification.NotificationAppender;
import com.bionote.notification.NotificationJsonCodec;
import com.bionote.notification.NotificationStore;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaNotificationAdapter implements NotificationAppender, NotificationStore {
    private final NotificationJpaRepository notifications;
    private final NotificationJsonCodec json;

    public JpaNotificationAdapter(
            NotificationJpaRepository notifications, NotificationJsonCodec json) {
        this.notifications = notifications;
        this.json = json;
    }

    @Override
    @Transactional
    public void appendIfAbsent(
            UUID id,
            UUID recipientId,
            String type,
            String title,
            String body,
            Map<String, ?> payload,
            String dedupKey,
            Instant createdAt) {
        notifications.appendIfAbsent(
                id.toString(),
                recipientId.toString(),
                type,
                title,
                body,
                json.encode(payload),
                dedupKey,
                createdAt);
    }

    @Override
    public PageSlice findByRecipient(UUID recipientId, boolean unreadOnly, int page, int size) {
        var result =
                unreadOnly
                        ? notifications.findByRecipientIdAndReadAtIsNullOrderByCreatedAtDescIdDesc(
                                recipientId, PageRequest.of(page, size))
                        : notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(
                                recipientId, PageRequest.of(page, size));
        return new PageSlice(
                result.getContent().stream().map(this::map).toList(), result.getTotalElements());
    }

    @Override
    public long countUnread(UUID recipientId) {
        return notifications.countByRecipientIdAndReadAtIsNull(recipientId);
    }

    @Override
    public boolean markRead(UUID id, UUID recipientId, Instant readAt) {
        return notifications.markRead(id, recipientId, readAt) == 1;
    }

    @Override
    public void markAllRead(UUID recipientId, Instant readAt) {
        notifications.markAllRead(recipientId, readAt);
    }

    private NotificationRecord map(NotificationEntity value) {
        return new NotificationRecord(
                value.id,
                value.recipientId,
                value.type,
                value.title,
                value.body,
                value.payloadJson,
                value.dedupKey,
                value.createdAt,
                value.readAt);
    }
}
