package com.bionote.review;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for review assignment and decision state. */
public interface ReviewStore {
    void append(ReviewRecord review);
    boolean existsPending(UUID recordId);
    Optional<ReviewRecord> findByRevisionId(UUID revisionId);
    Optional<ReviewRecord> findById(UUID reviewId);
    Optional<ReviewRecord> findByIdForUpdate(UUID reviewId);
    boolean decidePending(UUID reviewId, String status, String comment, Instant decidedAt);
    List<PendingReviewRecord> pendingForReviewer(UUID reviewerId);
    List<PendingAssignmentRecord> pendingAssignments(UUID projectId, UUID reviewerId);
    boolean reassignPending(UUID reviewId, UUID currentReviewerId, UUID nextReviewerId);

    record ReviewRecord(UUID id, UUID recordId, UUID revisionId, UUID reviewerId, String status,
                        String decisionComment, Instant assignedAt, Instant decidedAt) {}
    record PendingReviewRecord(UUID reviewId, UUID recordId, String recordCode, String recordTitle,
                               UUID projectId, String projectName, int revisionNo, Instant assignedAt) {}
    record PendingAssignmentRecord(UUID reviewId, UUID recordId, UUID creatorId, String recordTitle,
                                   int revisionNo) {}
}
