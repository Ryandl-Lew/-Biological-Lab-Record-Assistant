package com.bionote.revision.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "record_revisions")
class RecordRevisionEntity {
    @Id @JdbcTypeCode(SqlTypes.CHAR) UUID id;
    @Column(name = "record_id", nullable = false) @JdbcTypeCode(SqlTypes.CHAR) UUID recordId;
    @Column(name = "revision_no", nullable = false) int revisionNo;
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT") String snapshotJson;
    @Column(name = "snapshot_schema_version", nullable = false) int snapshotSchemaVersion;
    @Column(name = "content_hash", nullable = false, length = 64) @JdbcTypeCode(SqlTypes.CHAR) String contentHash;
    @Column(name = "submit_note", length = 2000) String submitNote;
    @Column(name = "submitted_by", nullable = false) @JdbcTypeCode(SqlTypes.CHAR) UUID submittedBy;
    @Column(name = "submitted_at", nullable = false) Instant submittedAt;
    @Column(name = "idempotency_key", length = 160) String idempotencyKey;

    protected RecordRevisionEntity() {}
    RecordRevisionEntity(UUID id, UUID recordId, int revisionNo, String snapshotJson,
                         int snapshotSchemaVersion, String contentHash, String submitNote,
                         UUID submittedBy, Instant submittedAt, String idempotencyKey) {
        this.id=id; this.recordId=recordId; this.revisionNo=revisionNo; this.snapshotJson=snapshotJson;
        this.snapshotSchemaVersion=snapshotSchemaVersion; this.contentHash=contentHash;
        this.submitNote=submitNote; this.submittedBy=submittedBy; this.submittedAt=submittedAt;
        this.idempotencyKey=idempotencyKey;
    }
}
