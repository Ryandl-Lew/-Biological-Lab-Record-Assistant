package com.bionote.domain.record;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
public final class RecordActionPolicy {
    private static final Set<String> EDITABLE = Set.of("IN_PROGRESS", "CHANGES_REQUESTED");

    public Decision canEdit(RecordContext context, UUID actorId) {
        Decision visible = requireVisibleMember(context);
        if (!visible.allowed()) return visible;
        if (!"ACTIVE".equals(context.projectStatus())) return Decision.deny("PROJECT_ARCHIVED", "项目已归档，只能查看");
        if (!context.creatorId().equals(actorId)) return Decision.deny("ACCESS_DENIED", "只有记录创建者可以编辑");
        if (!EDITABLE.contains(context.recordStatus())) return Decision.deny("RECORD_STATE_CONFLICT", "当前记录状态不可编辑");
        return Decision.allow();
    }

    public Decision canSubmit(RecordContext context, UUID actorId) {
        Decision edit = canEdit(context, actorId);
        if (!edit.allowed()) return edit;
        if (context.provisional()) return Decision.deny("RECORD_STATE_CONFLICT", "请先保存记录再提交审核");
        return Decision.allow();
    }

    public Decision canRestore(RecordContext context, UUID actorId) {
        Decision visible = requireVisibleMember(context);
        if (!visible.allowed()) return visible;
        if (!"ACTIVE".equals(context.projectStatus())) return Decision.deny("RESTORE_NOT_ALLOWED", "项目已归档，不能恢复记录");
        if (!context.creatorId().equals(actorId)) return Decision.deny("ACCESS_DENIED", "只有记录创建者可以恢复工作副本");
        if (context.provisional()) return Decision.deny("RESTORE_NOT_ALLOWED", "临时记录没有可恢复的历史修订");
        if (!EDITABLE.contains(context.recordStatus())) return Decision.deny("RESTORE_NOT_ALLOWED", "当前记录状态不允许恢复");
        return Decision.allow();
    }

    public Decision canViewRevision(RecordContext context, UUID actorId) { return requireVisibleMember(context); }

    public Decision canGenerateRecordSummary(RecordContext context, UUID actorId) {
        Decision visible = requireVisibleMember(context);
        if (!visible.allowed()) return visible;
        if (!context.creatorId().equals(actorId)) return Decision.deny("ACCESS_DENIED", "只有记录创建者可以生成记录总结");
        if (context.provisional()) return Decision.deny("AGENT_INVALID_REQUEST", "临时记录不能生成总结");
        return Decision.allow();
    }

    private Decision requireVisibleMember(RecordContext context) {
        if (context == null || context.deleted() || context.actorProjectRole() == null || context.actorProjectRole().isBlank()) {
            return Decision.deny("RESOURCE_NOT_FOUND", "记录不存在或无权访问");
        }
        return Decision.allow();
    }
}
