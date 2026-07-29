package com.bionote.review;

import java.util.List;
import java.util.UUID;

public interface ReviewUseCase {
    List<ReviewDtos.Candidate> candidates(UUID userId, UUID recordId);
    ReviewDtos.RevisionView submit(UUID userId, UUID recordId, ReviewDtos.SubmitRequest request, String idempotencyKey);
    ReviewDtos.RevisionView requestChanges(UUID userId, UUID recordId, UUID reviewId, String comment);
    ReviewDtos.RevisionView approve(UUID userId, UUID recordId, UUID reviewId, String comment);
    List<ReviewDtos.PendingReview> pending(UUID userId);
}
