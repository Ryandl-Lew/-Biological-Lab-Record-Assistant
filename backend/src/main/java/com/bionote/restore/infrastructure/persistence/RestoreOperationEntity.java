package com.bionote.restore.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="record_restore_operations")
class RestoreOperationEntity {
    @Id @JdbcTypeCode(SqlTypes.CHAR) UUID id;
    @Column(name="record_id",nullable=false) @JdbcTypeCode(SqlTypes.CHAR) UUID recordId;
    @Column(name="source_revision_id",nullable=false) @JdbcTypeCode(SqlTypes.CHAR) UUID sourceRevisionId;
    @Column(name="actor_id",nullable=false) @JdbcTypeCode(SqlTypes.CHAR) UUID actorId;
    @Column(name="before_record_version",nullable=false) long beforeVersion;
    @Column(name="after_record_version",nullable=false) long afterVersion;
    @Column(name="before_content_hash",nullable=false,length=64) @JdbcTypeCode(SqlTypes.CHAR) String beforeHash;
    @Column(name="after_content_hash",nullable=false,length=64) @JdbcTypeCode(SqlTypes.CHAR) String afterHash;
    @Column(name="diff_summary_json",nullable=false,columnDefinition="TEXT") String diffSummaryJson;
    @Column(name="idempotency_key",nullable=false,length=160) String idempotencyKey;
    @Column(name="payload_hash",nullable=false,length=64) @JdbcTypeCode(SqlTypes.CHAR) String payloadHash;
    @Column(name="restored_at",nullable=false) Instant restoredAt;
    protected RestoreOperationEntity(){}
    RestoreOperationEntity(UUID id,UUID recordId,UUID sourceRevisionId,UUID actorId,long beforeVersion,long afterVersion,
                           String beforeHash,String afterHash,String diffSummaryJson,String idempotencyKey,String payloadHash,
                           Instant restoredAt){this.id=id;this.recordId=recordId;this.sourceRevisionId=sourceRevisionId;
        this.actorId=actorId;this.beforeVersion=beforeVersion;this.afterVersion=afterVersion;this.beforeHash=beforeHash;
        this.afterHash=afterHash;this.diffSummaryJson=diffSummaryJson;this.idempotencyKey=idempotencyKey;
        this.payloadHash=payloadHash;this.restoredAt=restoredAt;}
}
