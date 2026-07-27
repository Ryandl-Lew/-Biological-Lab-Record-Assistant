package com.bionote.domain.record;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecordActionPolicyTest {
    private final RecordActionPolicy policy = new RecordActionPolicy();
    private final UUID recordId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final UUID creatorId = UUID.randomUUID();

    @Test void creatorCanRestoreActiveInProgressRecord() { assertAllowed(policy.canRestore(context("IN_PROGRESS", "ACTIVE", false, "MEMBER", creatorId), creatorId)); }
    @Test void creatorCanRestoreChangesRequestedRecord() { assertAllowed(policy.canRestore(context("CHANGES_REQUESTED", "ACTIVE", false, "OWNER", creatorId), creatorId)); }

    @Test void ownerCannotRestoreAnotherCreatorsRecord() {
        Decision decision = policy.canRestore(context("IN_PROGRESS", "ACTIVE", false, "OWNER", UUID.randomUUID()), creatorId);
        assertDenied(decision, "ACCESS_DENIED", "只有记录创建者可以恢复工作副本");
    }

    @Test void reviewerCannotRestoreAnotherCreatorsRecord() {
        Decision decision = policy.canRestore(context("IN_PROGRESS", "ACTIVE", false, "REVIEWER", UUID.randomUUID()), creatorId);
        assertDenied(decision, "ACCESS_DENIED", "只有记录创建者可以恢复工作副本");
    }

    @Test void inReviewCannotBeRestored() { assertDenied(policy.canRestore(context("IN_REVIEW", "ACTIVE", false, "MEMBER", creatorId), creatorId), "RESTORE_NOT_ALLOWED", "当前记录状态不允许恢复"); }
    @Test void completedCannotBeRestored() { assertDenied(policy.canRestore(context("COMPLETED", "ACTIVE", false, "MEMBER", creatorId), creatorId), "RESTORE_NOT_ALLOWED", "当前记录状态不允许恢复"); }
    @Test void archivedProjectCannotBeRestored() { assertDenied(policy.canRestore(context("IN_PROGRESS", "ARCHIVED", false, "OWNER", creatorId), creatorId), "RESTORE_NOT_ALLOWED", "项目已归档，不能恢复记录"); }
    @Test void provisionalRecordCannotBeRestored() { assertDenied(policy.canRestore(context("IN_PROGRESS", "ACTIVE", true, "MEMBER", creatorId), creatorId), "RESTORE_NOT_ALLOWED", "临时记录没有可恢复的历史修订"); }

    @Test void nonMemberGetsInvisibleResourceDecision() {
        assertDenied(policy.canRestore(context("IN_PROGRESS", "ACTIVE", false, null, creatorId), creatorId), "RESOURCE_NOT_FOUND", "记录不存在或无权访问");
    }

    private RecordContext context(String recordStatus, String projectStatus, boolean provisional, String role, UUID creator) {
        return new RecordContext(recordId, projectId, creator, recordStatus, projectStatus, false, provisional, role);
    }
    private void assertAllowed(Decision decision) { assertThat(decision.allowed()).isTrue(); assertThat(decision.errorCode()).isNull(); assertThat(decision.message()).isNull(); }
    private void assertDenied(Decision decision, String code, String message) { assertThat(decision.allowed()).isFalse(); assertThat(decision.errorCode()).isEqualTo(code); assertThat(decision.message()).isEqualTo(message); }
}
