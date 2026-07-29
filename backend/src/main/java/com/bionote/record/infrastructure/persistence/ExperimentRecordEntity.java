package com.bionote.record.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "experiment_records")
class ExperimentRecordEntity {
    @Id @JdbcTypeCode(SqlTypes.CHAR) UUID id;
    @Column(nullable = false, length = 40) String code;
    @Column(name = "project_id", nullable = false) @JdbcTypeCode(SqlTypes.CHAR) UUID projectId;
    @Column(name = "creator_id", nullable = false) @JdbcTypeCode(SqlTypes.CHAR) UUID creatorId;
    @Column(nullable = false, length = 255) String title;
    @Column(name = "experiment_type", nullable = false, length = 100) String experimentType;
    @Column(name = "experiment_date", nullable = false) LocalDate experimentDate;
    @Column(nullable = false, length = 3000) String purpose;
    @Column(nullable = false, length = 30) String status;
    @Column(nullable = false) boolean provisional;
    @Column(name = "template_snapshot_json", nullable = false, columnDefinition = "TEXT") String templateSnapshotJson;
    @Column(name = "field_values_json", nullable = false, columnDefinition = "TEXT") String fieldValuesJson;
    @Column(name = "content_json", nullable = false, columnDefinition = "TEXT") String contentJson;
    @Column(name = "content_html_sanitized", nullable = false, columnDefinition = "TEXT") String contentHtmlSanitized;
    @Column(name = "content_plain_text", nullable = false, columnDefinition = "TEXT") String contentPlainText;
    @Column(name = "current_revision_no", nullable = false) int currentRevisionNo;
    @Column(name = "current_review_id") @JdbcTypeCode(SqlTypes.CHAR) UUID currentReviewId;
    @Column(name = "final_revision_id") @JdbcTypeCode(SqlTypes.CHAR) UUID finalRevisionId;
    @Version long version;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
    @Column(name = "deleted_at") Instant deletedAt;

    protected ExperimentRecordEntity() {}

    ExperimentRecordEntity(UUID id, String code, UUID projectId, UUID creatorId, String title, String experimentType,
                           LocalDate experimentDate, String purpose, boolean provisional, String templateSnapshotJson,
                           String fieldValuesJson, String contentJson, String contentHtmlSanitized,
                           String contentPlainText, Instant now) {
        this.id=id; this.code=code; this.projectId=projectId; this.creatorId=creatorId; this.title=title;
        this.experimentType=experimentType; this.experimentDate=experimentDate; this.purpose=purpose;
        this.status="IN_PROGRESS"; this.provisional=provisional; this.templateSnapshotJson=templateSnapshotJson;
        this.fieldValuesJson=fieldValuesJson; this.contentJson=contentJson;
        this.contentHtmlSanitized=contentHtmlSanitized; this.contentPlainText=contentPlainText;
        this.currentRevisionNo=0; this.createdAt=now; this.updatedAt=now;
    }

    void updateWorkingCopy(String title, String experimentType, LocalDate experimentDate, String purpose,
                           String fieldValuesJson, String contentJson, String contentHtmlSanitized,
                           String contentPlainText, boolean provisional, Instant now) {
        this.title=title; this.experimentType=experimentType; this.experimentDate=experimentDate; this.purpose=purpose;
        this.fieldValuesJson=fieldValuesJson; this.contentJson=contentJson;
        this.contentHtmlSanitized=contentHtmlSanitized; this.contentPlainText=contentPlainText;
        this.provisional=provisional; this.updatedAt=now;
    }

    void softDelete(Instant now) { this.deletedAt=now; this.updatedAt=now; }
}
