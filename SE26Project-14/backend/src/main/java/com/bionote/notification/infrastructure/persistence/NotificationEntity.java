package com.bionote.notification.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "notifications")
class NotificationEntity {
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID id;

    @Column(name = "recipient_id", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID recipientId;

    @Column(nullable = false, length = 50)
    String type;

    @Column(nullable = false, length = 200)
    String title;

    @Column(nullable = false, length = 1000)
    String body;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    String payloadJson;

    @Column(name = "dedup_key", nullable = false, length = 200, unique = true)
    String dedupKey;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "read_at")
    Instant readAt;

    protected NotificationEntity() {}
}
