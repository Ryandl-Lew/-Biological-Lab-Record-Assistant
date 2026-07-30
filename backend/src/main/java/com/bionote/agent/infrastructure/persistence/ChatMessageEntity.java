package com.bionote.agent.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_chat_messages")
public class ChatMessageEntity {
    @Id @JdbcTypeCode(SqlTypes.CHAR) UUID id;
    @Column(name = "session_id", nullable = false) @JdbcTypeCode(SqlTypes.CHAR) UUID sessionId;
    @Column(length = 20, nullable = false) String role;
    @Column(columnDefinition = "TEXT", nullable = false) String content;
    @Column(name = "created_at", nullable = false) Instant createdAt;

    public ChatMessageEntity() {}
    public ChatMessageEntity(UUID id, UUID sessionId, String role, String content, Instant createdAt) {
        this.id = id; this.sessionId = sessionId; this.role = role; this.content = content; this.createdAt = createdAt;
    }
    public UUID getId() { return id; }
    public UUID getSessionId() { return sessionId; }
    public String getRole() { return role; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
