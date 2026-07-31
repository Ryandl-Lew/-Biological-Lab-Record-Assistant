package com.bionote.project.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "project_invitations")
class ProjectInvitationEntity {
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID id;

    @Column(name = "project_id", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID projectId;

    @Column(name = "inviter_id", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID inviterId;

    @Column(name = "invitee_user_id", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID inviteeUserId;

    @Column(name = "invitee_email_snapshot", nullable = false, length = 255)
    String inviteeEmailSnapshot;

    @Column(nullable = false, length = 20)
    String status;

    @Column(name = "expires_at", nullable = false)
    Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "responded_at")
    Instant respondedAt;

    @Column(name = "pending_key", length = 100)
    String pendingKey;

    protected ProjectInvitationEntity() {}

    ProjectInvitationEntity(
            UUID id,
            UUID projectId,
            UUID inviterId,
            UUID inviteeUserId,
            String inviteeEmailSnapshot,
            String status,
            Instant expiresAt,
            Instant createdAt,
            Instant respondedAt,
            String pendingKey) {
        this.id = id;
        this.projectId = projectId;
        this.inviterId = inviterId;
        this.inviteeUserId = inviteeUserId;
        this.inviteeEmailSnapshot = inviteeEmailSnapshot;
        this.status = status;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.respondedAt = respondedAt;
        this.pendingKey = pendingKey;
    }
}
