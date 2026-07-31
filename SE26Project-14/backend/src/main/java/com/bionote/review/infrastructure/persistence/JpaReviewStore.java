package com.bionote.review.infrastructure.persistence;

import com.bionote.review.ReviewStore;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaReviewStore implements ReviewStore {
    private final ReviewJpaRepository reviews;

    public JpaReviewStore(ReviewJpaRepository reviews) {
        this.reviews = reviews;
    }

    @Override
    public void append(ReviewRecord value) {
        reviews.saveAndFlush(
                new ReviewEntity(
                        value.id(),
                        value.recordId(),
                        value.revisionId(),
                        value.reviewerId(),
                        value.status(),
                        value.decisionComment(),
                        value.assignedAt(),
                        value.decidedAt()));
    }

    @Override
    public boolean existsPending(UUID recordId) {
        return reviews.existsByRecordIdAndStatus(recordId, "PENDING");
    }

    @Override
    public Optional<ReviewRecord> findByRevisionId(UUID revisionId) {
        return reviews.findByRevisionId(revisionId).map(this::map);
    }

    @Override
    public Optional<ReviewRecord> findById(UUID reviewId) {
        return reviews.findById(reviewId).map(this::map);
    }

    @Override
    public Optional<ReviewRecord> findByIdForUpdate(UUID reviewId) {
        return reviews.findByIdForUpdate(reviewId).map(this::map);
    }

    @Override
    public boolean decidePending(UUID reviewId, String status, String comment, Instant decidedAt) {
        return reviews.decidePending(reviewId, status, comment, decidedAt) == 1;
    }

    @Override
    public List<PendingReviewRecord> pendingForReviewer(UUID reviewerId) {
        return reviews.pendingForReviewer(reviewerId.toString()).stream()
                .map(
                        row ->
                                new PendingReviewRecord(
                                        uuid(row.getReviewId()),
                                        uuid(row.getRecordId()),
                                        row.getRecordCode(),
                                        row.getRecordTitle(),
                                        uuid(row.getProjectId()),
                                        row.getProjectName(),
                                        row.getRevisionNo(),
                                        row.getAssignedAt()))
                .toList();
    }

    @Override
    public List<PendingAssignmentRecord> pendingAssignments(UUID projectId, UUID reviewerId) {
        return reviews.pendingAssignments(projectId.toString(), reviewerId.toString()).stream()
                .map(
                        row ->
                                new PendingAssignmentRecord(
                                        uuid(row.getReviewId()),
                                        uuid(row.getRecordId()),
                                        uuid(row.getCreatorId()),
                                        row.getRecordTitle(),
                                        row.getRevisionNo()))
                .toList();
    }

    @Override
    public boolean reassignPending(UUID reviewId, UUID currentReviewerId, UUID nextReviewerId) {
        return reviews.reassignPending(reviewId, currentReviewerId, nextReviewerId) == 1;
    }

    private ReviewRecord map(ReviewEntity value) {
        return new ReviewRecord(
                value.id,
                value.recordId,
                value.revisionId,
                value.reviewerId,
                value.status,
                value.decisionComment,
                value.assignedAt,
                value.decidedAt);
    }

    private UUID uuid(String value) {
        return UUID.fromString(value);
    }
}
