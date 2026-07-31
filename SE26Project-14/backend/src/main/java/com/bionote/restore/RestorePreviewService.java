package com.bionote.restore;

import com.bionote.common.ApiException;
import com.bionote.domain.record.Decision;
import com.bionote.domain.record.RecordActionPolicy;
import com.bionote.domain.record.RecordContext;
import com.bionote.project.ProjectMemberStore;
import com.bionote.project.ProjectStore;
import com.bionote.record.RecordStore;
import com.bionote.revision.NormalizedRecordSnapshot;
import com.bionote.revision.RevisionDiffUseCase;
import com.bionote.revision.RevisionStore;
import com.bionote.revision.SnapshotNormalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RestorePreviewService implements RestorePreviewUseCase {
    private final RecordActionPolicy policy;
    private final RevisionStore revisions;
    private final RecordStore records;
    private final ProjectStore projects;
    private final ProjectMemberStore members;
    private final SnapshotNormalizer normalizer;
    private final RevisionDiffUseCase diffs;
    private final AttachmentRestorePlanner attachments;
    private final WorkingCopySnapshotWriter writer;
    private final RestorePreviewTokenService tokens;

    public RestorePreviewService(
            RecordActionPolicy policy,
            RevisionStore revisions,
            RecordStore records,
            ProjectStore projects,
            ProjectMemberStore members,
            SnapshotNormalizer normalizer,
            RevisionDiffUseCase diffs,
            AttachmentRestorePlanner attachments,
            WorkingCopySnapshotWriter writer,
            RestorePreviewTokenService tokens) {
        this.policy = policy;
        this.revisions = revisions;
        this.records = records;
        this.projects = projects;
        this.members = members;
        this.normalizer = normalizer;
        this.diffs = diffs;
        this.attachments = attachments;
        this.writer = writer;
        this.tokens = tokens;
    }

    public RestoreDtos.Preview preview(
            UUID actorId, UUID recordId, RestoreDtos.PreviewRequest request) {
        RecordStore.RecordData record = record(recordId);
        authorize(actorId, record);
        long version = record.version();
        if (version != request.expectedRecordVersion())
            throw new ApiException(
                    HttpStatus.CONFLICT, "OPTIMISTIC_LOCK_CONFLICT", "记录已被其他页面更新，请重新预览");
        RevisionStore.SourceRecord source =
                revisions.revisionSource(actorId, recordId, request.sourceRevisionId());
        RevisionStore.SourceRecord current = revisions.workingCopySource(actorId, recordId);
        NormalizedRecordSnapshot sourceNormalized = normalizer.normalize(source),
                currentNormalized = normalizer.normalize(current);
        AttachmentRestorePlanner.Plan plan =
                attachments.plan(
                        recordId,
                        request.sourceRevisionId(),
                        source.snapshot(),
                        request.restoreAttachments());
        Set<UUID> finalFiles = request.restoreAttachments() ? plan.source() : plan.currentActive();
        WorkingCopySnapshotWriter.Prepared prepared =
                writer.prepare(record, source.snapshot(), finalFiles);
        NormalizedRecordSnapshot actualTarget =
                normalizedTarget(
                        source,
                        prepared,
                        request.restoreAttachments()
                                ? source.attachments()
                                : current.attachments());
        boolean noChanges = currentNormalized.canonicalHash().equals(actualTarget.canonicalHash());
        List<String> warnings = warnings(plan, request.restoreAttachments(), noChanges);
        boolean canExecute =
                (!request.restoreAttachments() || plan.dto().missingPhysicalFiles().isEmpty())
                        && !noChanges;
        var diff =
                diffs.compareWorkingCopyToRevision(
                        actorId, recordId, request.sourceRevisionId(), false);
        UUID projectId = record.projectId();
        var token =
                tokens.issue(
                        actorId,
                        recordId,
                        projectId,
                        request.sourceRevisionId(),
                        version,
                        request.restoreAttachments(),
                        currentNormalized.canonicalHash(),
                        sourceNormalized.canonicalHash());
        return new RestoreDtos.Preview(
                recordId,
                new RestoreDtos.SourceRevision(
                        request.sourceRevisionId(), source.source().revisionNo()),
                version,
                token.token(),
                token.expiresAt(),
                diff,
                plan.dto(),
                warnings,
                Map.of("canExecute", canExecute));
    }

    private NormalizedRecordSnapshot normalizedTarget(
            RevisionStore.SourceRecord source,
            WorkingCopySnapshotWriter.Prepared prepared,
            List<com.bionote.revision.RevisionDtos.AttachmentView> targetAttachments) {
        Map<String, Object> raw = new LinkedHashMap<>(source.snapshot());
        raw.put("fieldValues", prepared.fieldValues());
        return normalizer.normalize(
                new RevisionStore.SourceRecord(
                        source.source(),
                        source.schemaVersion(),
                        raw,
                        targetAttachments,
                        source.review(),
                        source.submitNote(),
                        null));
    }

    private List<String> warnings(
            AttachmentRestorePlanner.Plan plan, boolean restoreAttachments, boolean noChanges) {
        List<String> values = new ArrayList<>();
        if (restoreAttachments && !plan.dto().activate().isEmpty())
            values.add("将重新启用 " + plan.dto().activate().size() + " 个历史附件");
        if (restoreAttachments && !plan.dto().softDelete().isEmpty())
            values.add("将软删除当前工作副本新增的 " + plan.dto().softDelete().size() + " 个附件");
        if (!restoreAttachments && !plan.dto().droppedFileFieldReferences().isEmpty())
            values.add("未恢复附件，历史 FILE 字段中的无效引用将被丢弃");
        if (!plan.dto().missingPhysicalFiles().isEmpty()) values.add("来源修订包含物理文件缺失的附件；完整附件恢复已禁用");
        if (noChanges) values.add("当前工作副本已与该版本一致");
        return List.copyOf(values);
    }

    private RecordStore.RecordData record(UUID id) {
        return records.findActive(id)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "记录不存在或无权访问"));
    }

    private void authorize(UUID actorId, RecordStore.RecordData record) {
        String role = members.findRole(record.projectId(), actorId).orElse(null);
        String projectStatus =
                projects.findById(record.projectId())
                        .map(ProjectStore.ProjectRecord::status)
                        .orElse("");
        Decision decision =
                policy.canRestore(
                        new RecordContext(
                                record.id(),
                                record.projectId(),
                                record.creatorId(),
                                record.status(),
                                projectStatus,
                                false,
                                record.provisional(),
                                role),
                        actorId);
        if (!decision.allowed()) throw policyException(decision);
    }

    static ApiException policyException(Decision decision) {
        HttpStatus status =
                "RESOURCE_NOT_FOUND".equals(decision.errorCode())
                        ? HttpStatus.NOT_FOUND
                        : "ACCESS_DENIED".equals(decision.errorCode())
                                ? HttpStatus.FORBIDDEN
                                : HttpStatus.CONFLICT;
        return new ApiException(status, decision.errorCode(), decision.message());
    }
}
