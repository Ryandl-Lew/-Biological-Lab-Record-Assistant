package com.bionote.review;

import com.bionote.collaboration.CollaborationEvents;
import com.bionote.common.ApiException;
import com.bionote.project.ProjectMemberStore;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ReviewAssignmentService implements ReviewAssignmentUseCase {
    private final ReviewStore reviews;
    private final ProjectMemberStore members;
    private final CollaborationEvents events;

    public ReviewAssignmentService(
            ReviewStore reviews, ProjectMemberStore members, CollaborationEvents events) {
        this.reviews = reviews;
        this.members = members;
        this.events = events;
    }

    @Override
    public int reassignPending(
            UUID actor, UUID project, UUID currentReviewer, Map<UUID, UUID> assignments) {
        List<ReviewStore.PendingAssignmentRecord> pending =
                reviews.pendingAssignments(project, currentReviewer);
        if (pending.isEmpty()) return 0;
        Map<UUID, UUID> safe = assignments == null ? Map.of() : assignments;
        Set<UUID> expected = new HashSet<>();
        pending.forEach(row -> expected.add(row.reviewId()));
        if (!safe.keySet().equals(expected))
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "REVIEW_REASSIGNMENT_REQUIRED",
                    "该成员仍有待审核任务，必须逐条指定新审核人",
                    Map.of("pendingReviewIds", expected.toString()));
        for (ReviewStore.PendingAssignmentRecord row : pending) {
            UUID review = row.reviewId(), next = safe.get(review), creator = row.creatorId();
            if (next == null || next.equals(currentReviewer) || next.equals(creator)) invalid();
            String role = members.findRole(project, next).orElse(null);
            if (role == null || !Set.of("OWNER", "REVIEWER").contains(role)) invalid();
            if (!reviews.reassignPending(review, currentReviewer, next))
                throw new ApiException(
                        HttpStatus.CONFLICT, "REVIEW_REASSIGNMENT_REQUIRED", "审核任务状态已变化，请刷新后重试");
            events.notify(
                    next,
                    "REVIEW_REASSIGNED",
                    "审核任务已重指派",
                    row.recordTitle() + " 的 R" + row.revisionNo() + " 已指派给你",
                    Map.of("recordId", row.recordId().toString(), "reviewId", review.toString()),
                    "review-reassigned:" + review + ":" + next);
            events.notify(
                    currentReviewer,
                    "REVIEW_REASSIGNED_AWAY",
                    "审核任务已移交",
                    row.recordTitle() + " 的审核任务已由项目负责人移交",
                    Map.of("recordId", row.recordId().toString(), "reviewId", review.toString()),
                    "review-reassigned-away:" + review + ":" + currentReviewer);
            events.audit(
                    actor,
                    project,
                    row.recordId(),
                    "REVIEW_REASSIGNED",
                    "REVIEW",
                    review,
                    Map.of("from", currentReviewer.toString(), "to", next.toString()));
        }
        return pending.size();
    }

    private void invalid() {
        throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_REVIEWER",
                "新审核人必须是同项目 OWNER 或 REVIEWER，且不能是记录创建者");
    }
}
