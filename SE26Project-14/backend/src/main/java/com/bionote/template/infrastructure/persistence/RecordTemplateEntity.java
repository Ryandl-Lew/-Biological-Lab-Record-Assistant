package com.bionote.template.infrastructure.persistence;

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
@Table(name = "record_templates")
class RecordTemplateEntity {
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID id;

    @Column(nullable = false, length = 20)
    String scope;

    @Column(name = "owner_id")
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID ownerId;

    @Column(nullable = false, length = 120)
    String name;

    @Column(name = "name_normalized", nullable = false, length = 120)
    String nameNormalized;

    @Column(name = "active_name_key", length = 300)
    String activeNameKey;

    @Column(name = "experiment_type", length = 100)
    String experimentType;

    @Column(length = 80)
    String category;

    @Column(length = 2000)
    String description;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @Column(name = "deleted_at")
    Instant deletedAt;

    @Version long version;

    protected RecordTemplateEntity() {}

    RecordTemplateEntity(
            UUID id,
            UUID ownerId,
            String name,
            String nameNormalized,
            String activeNameKey,
            String experimentType,
            String category,
            String description,
            Instant now) {
        this.id = id;
        this.scope = "PERSONAL";
        this.ownerId = ownerId;
        this.name = name;
        this.nameNormalized = nameNormalized;
        this.activeNameKey = activeNameKey;
        this.experimentType = experimentType;
        this.category = category;
        this.description = description;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void update(
            String name,
            String nameNormalized,
            String activeNameKey,
            String experimentType,
            String category,
            String description,
            Instant now) {
        this.name = name;
        this.nameNormalized = nameNormalized;
        this.activeNameKey = activeNameKey;
        this.experimentType = experimentType;
        this.category = category;
        this.description = description;
        this.updatedAt = now;
    }

    void softDelete(Instant now) {
        this.deletedAt = now;
        this.activeNameKey = null;
        this.updatedAt = now;
    }
}
