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
@Table(name = "agent_chat_references")
class AgentChatReferenceEntity {
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID id;

    @Column(name = "project_id", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID projectId;

    @Column(name = "uploaded_by", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID uploadedBy;

    @Column(name = "storage_key", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    String storageKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    String originalFilename;

    @Column(name = "content_type", nullable = false, length = 120)
    String contentType;

    @Column(name = "size_bytes", nullable = false)
    long sizeBytes;

    @Column(name = "peek_json", nullable = false, columnDefinition = "TEXT")
    String peekJson;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    Instant expiresAt;

    protected AgentChatReferenceEntity() {}

    AgentChatReferenceEntity(
            UUID id,
            UUID projectId,
            UUID uploadedBy,
            String storageKey,
            String originalFilename,
            String contentType,
            long sizeBytes,
            String peekJson,
            Instant createdAt,
            Instant expiresAt) {
        this.id = id;
        this.projectId = projectId;
        this.uploadedBy = uploadedBy;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.peekJson = peekJson;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }
}
