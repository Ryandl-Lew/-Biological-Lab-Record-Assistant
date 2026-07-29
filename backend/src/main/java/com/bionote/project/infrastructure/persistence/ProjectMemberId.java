package com.bionote.project.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
class ProjectMemberId implements Serializable {
    @Column(name = "project_id") @JdbcTypeCode(SqlTypes.CHAR) UUID projectId;
    @Column(name = "user_id") @JdbcTypeCode(SqlTypes.CHAR) UUID userId;

    protected ProjectMemberId() {}
    ProjectMemberId(UUID projectId, UUID userId) { this.projectId = projectId; this.userId = userId; }

    @Override public boolean equals(Object value) {
        return value instanceof ProjectMemberId other && Objects.equals(projectId, other.projectId) && Objects.equals(userId, other.userId);
    }
    @Override public int hashCode() { return Objects.hash(projectId, userId); }
}
