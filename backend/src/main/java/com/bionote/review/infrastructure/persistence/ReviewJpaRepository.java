package com.bionote.review.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ReviewJpaRepository extends JpaRepository<ReviewEntity, UUID> {
    boolean existsByRecordIdAndStatus(UUID recordId,String status);
    Optional<ReviewEntity> findByRevisionId(UUID revisionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReviewEntity r where r.id=:id")
    Optional<ReviewEntity> findByIdForUpdate(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ReviewEntity r set r.status=:status,r.decisionComment=:comment,r.decidedAt=:decidedAt where r.id=:id and r.status='PENDING'")
    int decidePending(@Param("id") UUID id,@Param("status") String status,
                      @Param("comment") String comment,@Param("decidedAt") Instant decidedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ReviewEntity r set r.reviewerId=:next where r.id=:id and r.reviewerId=:current and r.status='PENDING'")
    int reassignPending(@Param("id") UUID id,@Param("current") UUID current,@Param("next") UUID next);

    interface PendingReviewProjection {
        String getReviewId();String getRecordId();String getRecordCode();String getRecordTitle();
        String getProjectId();String getProjectName();int getRevisionNo();Instant getAssignedAt();
    }
    @Query(value = """
            SELECT v.id AS reviewId,r.id AS recordId,r.code AS recordCode,r.title AS recordTitle,
                   r.project_id AS projectId,p.name AS projectName,rv.revision_no AS revisionNo,
                   v.assigned_at AS assignedAt
              FROM reviews v JOIN experiment_records r ON r.id=v.record_id
              JOIN projects p ON p.id=r.project_id JOIN record_revisions rv ON rv.id=v.revision_id
              JOIN project_members pm ON pm.project_id=r.project_id AND pm.user_id=:userId
             WHERE v.reviewer_id=:userId AND v.status='PENDING' AND r.status='IN_REVIEW'
             ORDER BY v.assigned_at
            """, nativeQuery = true)
    List<PendingReviewProjection> pendingForReviewer(@Param("userId") String userId);

    interface PendingAssignmentProjection {
        String getReviewId();String getRecordId();String getCreatorId();String getRecordTitle();int getRevisionNo();
    }
    @Query(value = """
            SELECT v.id AS reviewId,v.record_id AS recordId,r.creator_id AS creatorId,
                   r.title AS recordTitle,rv.revision_no AS revisionNo
              FROM reviews v JOIN experiment_records r ON r.id=v.record_id
              JOIN record_revisions rv ON rv.id=v.revision_id
             WHERE r.project_id=:projectId AND v.reviewer_id=:reviewerId
               AND v.status='PENDING' AND r.status='IN_REVIEW'
            """, nativeQuery = true)
    List<PendingAssignmentProjection> pendingAssignments(@Param("projectId") String projectId,
                                                         @Param("reviewerId") String reviewerId);
}
