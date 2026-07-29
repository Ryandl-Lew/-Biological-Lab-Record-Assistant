package com.bionote.project.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_members")
class ProjectMemberEntity {
    @EmbeddedId ProjectMemberId id;
    @Column(nullable = false, length = 20) String role;
    @Column(name = "joined_at", nullable = false) Instant joinedAt;
    @Column(name = "last_active_at") Instant lastActiveAt;

    protected ProjectMemberEntity() {}
    ProjectMemberEntity(UUID projectId, UUID userId, String role, Instant joinedAt, Instant lastActiveAt) {
        this.id = new ProjectMemberId(projectId, userId);
        this.role = role;
        this.joinedAt = joinedAt;
        this.lastActiveAt = lastActiveAt;
    }
}
