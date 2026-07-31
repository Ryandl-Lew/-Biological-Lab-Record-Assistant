package com.bionote.restore.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RestoreOperationJpaRepository extends JpaRepository<RestoreOperationEntity, UUID> {
    interface StoredProjection {
        String getId();

        String getRecordId();

        String getSourceRevisionId();

        int getRevisionNo();

        String getActorId();

        String getActorName();

        long getBeforeVersion();

        long getAfterVersion();

        String getBeforeHash();

        String getAfterHash();

        String getDiffSummaryJson();

        String getPayloadHash();

        Instant getRestoredAt();
    }

    @Query(
            value =
                    """
            SELECT o.id,o.record_id AS recordId,o.source_revision_id AS sourceRevisionId,
                   rv.revision_no AS revisionNo,o.actor_id AS actorId,u.display_name AS actorName,
                   o.before_record_version AS beforeVersion,o.after_record_version AS afterVersion,
                   o.before_content_hash AS beforeHash,o.after_content_hash AS afterHash,
                   o.diff_summary_json AS diffSummaryJson,o.payload_hash AS payloadHash,o.restored_at AS restoredAt
              FROM record_restore_operations o JOIN record_revisions rv ON rv.id=o.source_revision_id
              JOIN users u ON u.id=o.actor_id
             WHERE o.record_id=:recordId AND o.idempotency_key=:key
            """,
            nativeQuery = true)
    Optional<StoredProjection> findStored(
            @Param("recordId") String recordId, @Param("key") String key);

    @Query(
            value =
                    """
            SELECT o.id,o.record_id AS recordId,o.source_revision_id AS sourceRevisionId,
                   rv.revision_no AS revisionNo,o.actor_id AS actorId,u.display_name AS actorName,
                   o.before_record_version AS beforeVersion,o.after_record_version AS afterVersion,
                   o.before_content_hash AS beforeHash,o.after_content_hash AS afterHash,
                   o.diff_summary_json AS diffSummaryJson,o.payload_hash AS payloadHash,o.restored_at AS restoredAt
              FROM record_restore_operations o JOIN record_revisions rv ON rv.id=o.source_revision_id
              JOIN users u ON u.id=o.actor_id WHERE o.record_id=:recordId
             ORDER BY o.restored_at DESC,o.id DESC
            """,
            countQuery = "SELECT COUNT(*) FROM record_restore_operations WHERE record_id=:recordId",
            nativeQuery = true)
    Page<StoredProjection> listStored(@Param("recordId") String recordId, Pageable pageable);
}
