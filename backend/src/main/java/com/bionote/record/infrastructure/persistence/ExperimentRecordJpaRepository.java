package com.bionote.record.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

interface ExperimentRecordJpaRepository extends JpaRepository<ExperimentRecordEntity, UUID> {
    interface IdProjection { String getId(); }

    @Query(value = """
            SELECT r.id FROM experiment_records r JOIN project_members pm ON pm.project_id=r.project_id
             WHERE pm.user_id=:userId AND r.deleted_at IS NULL AND r.provisional=FALSE
               AND (:projectId IS NULL OR r.project_id=:projectId)
               AND (:creatorId IS NULL OR r.creator_id=:creatorId)
               AND (:status IS NULL OR r.status=:status)
               AND (LOWER(r.title) LIKE CONCAT('%',:keyword,'%') ESCAPE '!'
                    OR LOWER(r.purpose) LIKE CONCAT('%',:keyword,'%') ESCAPE '!'
                    OR LOWER(r.experiment_type) LIKE CONCAT('%',:keyword,'%') ESCAPE '!')
             ORDER BY r.updated_at DESC,r.id DESC
            """, countQuery = """
            SELECT COUNT(*) FROM experiment_records r JOIN project_members pm ON pm.project_id=r.project_id
             WHERE pm.user_id=:userId AND r.deleted_at IS NULL AND r.provisional=FALSE
               AND (:projectId IS NULL OR r.project_id=:projectId)
               AND (:creatorId IS NULL OR r.creator_id=:creatorId)
               AND (:status IS NULL OR r.status=:status)
               AND (LOWER(r.title) LIKE CONCAT('%',:keyword,'%') ESCAPE '!'
                    OR LOWER(r.purpose) LIKE CONCAT('%',:keyword,'%') ESCAPE '!'
                    OR LOWER(r.experiment_type) LIKE CONCAT('%',:keyword,'%') ESCAPE '!')
            """, nativeQuery = true)
    Page<IdProjection> searchVisible(@Param("userId") String userId, @Param("projectId") String projectId,
                                     @Param("creatorId") String creatorId, @Param("status") String status,
                                     @Param("keyword") String escapedKeyword, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ExperimentRecordEntity r where r.id=:id and r.creatorId=:creatorId and r.provisional=true")
    int deleteProvisional(@Param("id") UUID id, @Param("creatorId") UUID creatorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ExperimentRecordEntity r where r.id=:id and r.deletedAt is null")
    Optional<ExperimentRecordEntity> findActiveByIdForUpdate(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE experiment_records SET field_values_json=:json WHERE id=:id", nativeQuery = true)
    int replaceFieldValuesWithoutVersion(@Param("id") String id, @Param("json") String json);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE experiment_records SET status='IN_REVIEW',current_revision_no=:revisionNo,
              current_review_id=:reviewId,updated_at=:now,version=version+1
             WHERE id=:id AND version=:expectedVersion
               AND status IN ('IN_PROGRESS','CHANGES_REQUESTED')
            """, nativeQuery = true)
    int markSubmitted(@Param("id") String id, @Param("expectedVersion") long expectedVersion,
                      @Param("revisionNo") int revisionNo, @Param("reviewId") String reviewId,
                      @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE experiment_records SET status='COMPLETED',final_revision_id=:revisionId,
              updated_at=:now,version=version+1
             WHERE id=:id AND current_review_id=:reviewId AND status='IN_REVIEW'
            """, nativeQuery = true)
    int markReviewApproved(@Param("id") String id, @Param("reviewId") String reviewId,
                           @Param("revisionId") String revisionId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE experiment_records SET status='CHANGES_REQUESTED',updated_at=:now,version=version+1
             WHERE id=:id AND current_review_id=:reviewId AND status='IN_REVIEW'
            """, nativeQuery = true)
    int markReviewChangesRequested(@Param("id") String id, @Param("reviewId") String reviewId,
                                   @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE experiment_records SET title=:title,experiment_type=:experimentType,
              experiment_date=:experimentDate,purpose=:purpose,template_snapshot_json=:templateJson,
              field_values_json=:fieldValuesJson,content_json=:contentJson,
              content_html_sanitized=:contentHtml,content_plain_text=:contentText,
              updated_at=:now,version=version+1
             WHERE id=:id AND version=:expectedVersion AND deleted_at IS NULL
            """, nativeQuery = true)
    int restoreWorkingCopy(@Param("id") String id,@Param("expectedVersion") long expectedVersion,
                           @Param("title") String title,@Param("experimentType") String experimentType,
                           @Param("experimentDate") LocalDate experimentDate,@Param("purpose") String purpose,
                           @Param("templateJson") String templateJson,@Param("fieldValuesJson") String fieldValuesJson,
                           @Param("contentJson") String contentJson,@Param("contentHtml") String contentHtml,
                           @Param("contentText") String contentText,@Param("now") Instant now);
}
