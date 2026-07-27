package com.bionote.restore;

import com.bionote.common.ApiException;
import com.bionote.domain.record.Decision;
import com.bionote.domain.record.RecordActionPolicy;
import com.bionote.domain.record.RecordContext;
import com.bionote.revision.NormalizedRecordSnapshot;
import com.bionote.revision.RevisionDiffService;
import com.bionote.revision.RevisionRepository;
import com.bionote.revision.SnapshotNormalizer;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class RestorePreviewService {
    private final JdbcTemplate jdbc; private final RecordActionPolicy policy; private final RevisionRepository revisions;
    private final SnapshotNormalizer normalizer; private final RevisionDiffService diffs; private final AttachmentRestorePlanner attachments;
    private final WorkingCopySnapshotWriter writer; private final RestorePreviewTokenService tokens;

    public RestorePreviewService(JdbcTemplate jdbc, RecordActionPolicy policy, RevisionRepository revisions,
                                 SnapshotNormalizer normalizer, RevisionDiffService diffs,
                                 AttachmentRestorePlanner attachments, WorkingCopySnapshotWriter writer,
                                 RestorePreviewTokenService tokens) {
        this.jdbc=jdbc; this.policy=policy; this.revisions=revisions; this.normalizer=normalizer; this.diffs=diffs;
        this.attachments=attachments; this.writer=writer; this.tokens=tokens;
    }

    public RestoreDtos.Preview preview(UUID actorId, UUID recordId, RestoreDtos.PreviewRequest request) {
        Map<String, Object> record = record(recordId); authorize(actorId, record);
        long version = ((Number) record.get("version")).longValue();
        if (version != request.expectedRecordVersion()) throw new ApiException(HttpStatus.CONFLICT, "OPTIMISTIC_LOCK_CONFLICT", "记录已被其他页面更新，请重新预览");
        RevisionRepository.SourceRecord source = revisions.revisionSource(actorId, recordId, request.sourceRevisionId());
        RevisionRepository.SourceRecord current = revisions.workingCopySource(actorId, recordId);
        NormalizedRecordSnapshot sourceNormalized = normalizer.normalize(source), currentNormalized = normalizer.normalize(current);
        AttachmentRestorePlanner.Plan plan = attachments.plan(recordId, request.sourceRevisionId(), source.snapshot(), request.restoreAttachments());
        Set<UUID> finalFiles = request.restoreAttachments() ? plan.source() : plan.currentActive();
        WorkingCopySnapshotWriter.Prepared prepared = writer.prepare(record, source.snapshot(), finalFiles);
        NormalizedRecordSnapshot actualTarget = normalizedTarget(source, prepared, request.restoreAttachments() ? source.attachments() : current.attachments());
        boolean noChanges = currentNormalized.canonicalHash().equals(actualTarget.canonicalHash());
        List<String> warnings = warnings(plan, request.restoreAttachments(), noChanges);
        boolean canExecute = (!request.restoreAttachments() || plan.dto().missingPhysicalFiles().isEmpty()) && !noChanges;
        var diff = diffs.compareWorkingCopyToRevision(actorId, recordId, request.sourceRevisionId(), false);
        UUID projectId = UUID.fromString(record.get("project_id").toString());
        var token = tokens.issue(actorId, recordId, projectId, request.sourceRevisionId(), version, request.restoreAttachments(),
                currentNormalized.canonicalHash(), sourceNormalized.canonicalHash());
        return new RestoreDtos.Preview(recordId, new RestoreDtos.SourceRevision(request.sourceRevisionId(), source.source().revisionNo()), version,
                token.token(), token.expiresAt(), diff, plan.dto(), warnings, Map.of("canExecute", canExecute));
    }

    private NormalizedRecordSnapshot normalizedTarget(RevisionRepository.SourceRecord source, WorkingCopySnapshotWriter.Prepared prepared,
                                                       List<com.bionote.revision.RevisionDtos.AttachmentView> targetAttachments) {
        Map<String, Object> raw = new LinkedHashMap<>(source.snapshot()); raw.put("fieldValues", prepared.fieldValues());
        return normalizer.normalize(new RevisionRepository.SourceRecord(source.source(), source.schemaVersion(), raw, targetAttachments, source.review(), source.submitNote(), null));
    }
    private List<String> warnings(AttachmentRestorePlanner.Plan plan, boolean restoreAttachments, boolean noChanges) {
        List<String> values = new ArrayList<>();
        if (restoreAttachments && !plan.dto().activate().isEmpty()) values.add("将重新启用 " + plan.dto().activate().size() + " 个历史附件");
        if (restoreAttachments && !plan.dto().softDelete().isEmpty()) values.add("将软删除当前工作副本新增的 " + plan.dto().softDelete().size() + " 个附件");
        if (!restoreAttachments && !plan.dto().droppedFileFieldReferences().isEmpty()) values.add("未恢复附件，历史 FILE 字段中的无效引用将被丢弃");
        if (!plan.dto().missingPhysicalFiles().isEmpty()) values.add("来源修订包含物理文件缺失的附件；完整附件恢复已禁用");
        if (noChanges) values.add("当前工作副本已与该版本一致");
        return List.copyOf(values);
    }
    private Map<String, Object> record(UUID id) { List<Map<String,Object>> rows=jdbc.queryForList("SELECT r.*,p.status project_status FROM experiment_records r JOIN projects p ON p.id=r.project_id WHERE r.id=? AND r.deleted_at IS NULL", id.toString()); if(rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","记录不存在或无权访问"); return rows.get(0); }
    private void authorize(UUID actorId, Map<String, Object> record) {
        List<String> roles=jdbc.queryForList("SELECT role FROM project_members WHERE project_id=? AND user_id=?",String.class,record.get("project_id"),actorId.toString()); String role=roles.isEmpty()?null:roles.get(0);
        Decision decision=policy.canRestore(new RecordContext(UUID.fromString(record.get("id").toString()),UUID.fromString(record.get("project_id").toString()),UUID.fromString(record.get("creator_id").toString()),record.get("status").toString(),record.get("project_status").toString(),false,Boolean.TRUE.equals(record.get("provisional")),role),actorId);
        if(!decision.allowed()) throw policyException(decision);
    }
    static ApiException policyException(Decision decision) { HttpStatus status="RESOURCE_NOT_FOUND".equals(decision.errorCode())?HttpStatus.NOT_FOUND:"ACCESS_DENIED".equals(decision.errorCode())?HttpStatus.FORBIDDEN:HttpStatus.CONFLICT; return new ApiException(status,decision.errorCode(),decision.message()); }
}
