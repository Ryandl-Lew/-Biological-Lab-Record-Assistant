package com.bionote.project.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "projects")
class ProjectEntity {
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID id;

    @Column(nullable = false, length = 120)
    String name;

    @Column(length = 2000)
    String description;

    @Column(name = "detailed_description", columnDefinition = "TEXT")
    String detailedDescription;

    @Column(nullable = false, length = 20)
    String status;

    @Column(name = "owner_id", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID ownerId;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @Column(name = "archived_at")
    Instant archivedAt;

    @Version long version;

    protected ProjectEntity() {}

    ProjectEntity(
            UUID id,
            String name,
            String description,
            String detailedDescription,
            UUID ownerId,
            Instant now) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.detailedDescription = detailedDescription;
        this.status = "ACTIVE";
        this.ownerId = ownerId;
        this.createdAt = now;
        this.updatedAt = now;
    }
}
