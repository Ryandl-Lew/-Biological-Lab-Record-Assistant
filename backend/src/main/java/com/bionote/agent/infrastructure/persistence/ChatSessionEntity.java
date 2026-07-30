package com.bionote.agent.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "agent_chat_sessions")
public class ChatSessionEntity {
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID id;

    @Column(name = "project_id", length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID projectId;

    @Column(name = "record_id", length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID recordId;

    @Column(name = "user_id", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID userId;

    @Column(length = 200, nullable = false)
    String title;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    public ChatSessionEntity() {}

    public ChatSessionEntity(
            UUID id,
            UUID projectId,
            UUID recordId,
            UUID userId,
            String title,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.projectId = projectId;
        this.recordId = recordId;
        this.userId = userId;
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getRecordId() {
        return recordId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
