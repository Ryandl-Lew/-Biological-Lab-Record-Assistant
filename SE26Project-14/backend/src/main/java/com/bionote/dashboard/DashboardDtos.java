package com.bionote.dashboard;

import java.time.Instant;
import java.util.UUID;

public final class DashboardDtos {
    private DashboardDtos() {}

    public record Task(
            String id,
            String type,
            UUID targetId,
            String title,
            UUID projectId,
            String projectName,
            Instant time,
            Integer revisionNo,
            String action,
            boolean stale) {}

    public record Summary(
            long projectCount,
            long editableRecordCount,
            long changesRequestedCount,
            long pendingReviewCount,
            long pendingInvitationCount,
            long unreadNotificationCount) {}
}
