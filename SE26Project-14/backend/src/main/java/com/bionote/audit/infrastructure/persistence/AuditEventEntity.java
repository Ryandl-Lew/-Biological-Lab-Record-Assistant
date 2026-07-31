package com.bionote.audit.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_events")
class AuditEventEntity {
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID id;

    @Column(name = "actor_id")
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID actorId;

    @Column(name = "project_id")
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID projectId;

    @Column(name = "record_id")
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID recordId;

    @Column(name = "event_type", nullable = false, length = 80)
    String eventType;

    @Column(name = "target_type", nullable = false, length = 50)
    String targetType;

    @Column(name = "target_id", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID targetId;

    @Column(name = "metadata_json", nullable = false, columnDefinition = "TEXT")
    String metadataJson;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    protected AuditEventEntity() {}

    AuditEventEntity(
            UUID id,
            UUID actorId,
            UUID projectId,
            UUID recordId,
            String eventType,
            String targetType,
            UUID targetId,
            String metadataJson,
            Instant createdAt) {
        this.id = id;
        this.actorId = actorId;
        this.projectId = projectId;
        this.recordId = recordId;
        this.eventType = eventType;
        this.targetType = targetType;
        this.targetId = targetId;
        this.metadataJson = metadataJson;
        this.createdAt = createdAt;
    }
}
