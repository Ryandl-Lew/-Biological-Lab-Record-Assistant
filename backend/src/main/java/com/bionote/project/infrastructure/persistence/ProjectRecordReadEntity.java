package com.bionote.project.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "experiment_records")
class ProjectRecordReadEntity {
    @Id @JdbcTypeCode(SqlTypes.CHAR) UUID id;
    @Column(name = "project_id", nullable = false) @JdbcTypeCode(SqlTypes.CHAR) UUID projectId;
    @Column(name = "creator_id", nullable = false) @JdbcTypeCode(SqlTypes.CHAR) UUID creatorId;
    @Column(nullable = false) String title;
    @Column(nullable = false) String status;
    @Column(nullable = false) boolean provisional;
    @Column(name = "deleted_at") Instant deletedAt;
}
