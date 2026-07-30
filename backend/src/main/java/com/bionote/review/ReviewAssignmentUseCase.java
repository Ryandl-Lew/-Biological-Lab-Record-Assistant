package com.bionote.review;

import java.util.Map;
import java.util.UUID;

public interface ReviewAssignmentUseCase {
    int reassignPending(
            UUID actorId, UUID projectId, UUID currentReviewerId, Map<UUID, UUID> assignments);
}
