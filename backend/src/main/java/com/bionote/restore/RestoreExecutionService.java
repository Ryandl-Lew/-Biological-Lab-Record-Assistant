package com.bionote.restore;

import com.bionote.collaboration.event.DomainEventPublisher;
import com.bionote.collaboration.event.RecordRevisionRestoredEvent;
import com.bionote.common.ApiException;
import com.bionote.domain.record.Decision;
import com.bionote.domain.record.RecordActionPolicy;
import com.bionote.domain.record.RecordContext;
import com.bionote.record.RecordService;
import com.bionote.revision.NormalizedRecordSnapshot;
import com.bionote.revision.RevisionDiffService;
import com.bionote.revision.RevisionRepository;
import com.bionote.revision.SnapshotNormalizer;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class RestoreExecutionService {
    private final JdbcTemplate jdbc; private final RecordActionPolicy policy; private final RevisionRepository revisions;
    private final SnapshotNormalizer normalizer; private final RevisionDiffService diffs; private final AttachmentRestorePlanner attachments;
    private final WorkingCopySnapshotWriter writer; private final RestorePreviewTokenService tokens;
    private final RestoreOperationRepository operations; private final DomainEventPublisher events; private final RecordService records;

    public RestoreExecutionService(JdbcTemplate jdbc, RecordActionPolicy policy, RevisionRepository revisions,
                                   SnapshotNormalizer normalizer, RevisionDiffService diffs, AttachmentRestorePlanner attachments,
                                   WorkingCopySnapshotWriter writer, RestorePreviewTokenService tokens,
                                   RestoreOperationRepository operations, DomainEventPublisher events, RecordService records) {
        this.jdbc=jdbc;this.policy=policy;this.revisions=revisions;this.normalizer=normalizer;this.diffs=diffs;this.attachments=attachments;
        this.writer=writer;this.tokens=tokens;this.operations=operations;this.events=events;this.records=records;
    }

    @Transactional
    public RestoreDtos.Result restore(UUID actorId, UUID recordId, RestoreDtos.ExecuteRequest request, String idempotencyKey) {
        String key = normalizeKey(idempotencyKey); String payloadHash = payloadHash(recordId, request);
        RestoreOperationRepository.Stored existing = operations.find(recordId, key);
        if (existing != null) return existing(actorId, recordId, payloadHash, existing);
        RestorePreviewTokenService.Claims claims = tokens.verify(request.previewToken()); validateClaims(actorId, recordId, request, claims);
        Map<String, Object> record = recordForUpdate(recordId); authorize(actorId, record);
        if (!UUID.fromString(record.get("project_id").toString()).equals(claims.projectId())) throw new ApiException(HttpStatus.CONFLICT,"RESTORE_PREVIEW_STALE","恢复预览与当前项目不一致");
        existing = operations.find(recordId, key); if (existing != null) return existing(actorId, recordId, payloadHash, existing);
        long version = ((Number) record.get("version")).longValue();
        if (version != request.expectedRecordVersion()) throw new ApiException(HttpStatus.CONFLICT,"RESTORE_PREVIEW_STALE","工作副本在预览后已变化，请重新预览");
        RevisionRepository.SourceRecord source = revisions.revisionSource(actorId, recordId, request.sourceRevisionId());
        RevisionRepository.SourceRecord current = revisions.workingCopySource(actorId, recordId);
        NormalizedRecordSnapshot sourceNormalized=normalizer.normalize(source), currentNormalized=normalizer.normalize(current);
        if (!claims.sourceHash().equals(sourceNormalized.canonicalHash())) throw new ApiException(HttpStatus.CONFLICT,"RESTORE_PREVIEW_STALE","来源修订完整性已变化");
        if (!claims.currentHash().equals(currentNormalized.canonicalHash())) throw new ApiException(HttpStatus.CONFLICT,"RESTORE_PREVIEW_STALE","工作副本在预览后已变化，请重新预览");
        AttachmentRestorePlanner.Plan plan=attachments.plan(recordId,request.sourceRevisionId(),source.snapshot(),request.restoreAttachments());
        if(request.restoreAttachments()&&!plan.dto().missingPhysicalFiles().isEmpty()) throw new ApiException(HttpStatus.CONFLICT,"RESTORE_NOT_ALLOWED","来源修订存在物理文件缺失，不能完整恢复附件");
        Set<UUID> finalFiles=request.restoreAttachments()?plan.source():plan.currentActive(); WorkingCopySnapshotWriter.Prepared prepared=writer.prepare(record,source.snapshot(),finalFiles);
        Map<String,Object> targetRaw=new LinkedHashMap<>(source.snapshot());targetRaw.put("fieldValues",prepared.fieldValues());
        NormalizedRecordSnapshot target=normalizer.normalize(new RevisionRepository.SourceRecord(source.source(),source.schemaVersion(),targetRaw,request.restoreAttachments()?source.attachments():current.attachments(),source.review(),source.submitNote(),null));
        if(currentNormalized.canonicalHash().equals(target.canonicalHash())) throw new ApiException(HttpStatus.CONFLICT,"RESTORE_NO_CHANGES","当前工作副本已与该版本一致");
        var diff=diffs.compareWorkingCopyToRevision(actorId,recordId,request.sourceRevisionId(),false); Instant now=Instant.now(); long after=writer.write(recordId,version,prepared,now);
        if(request.restoreAttachments()) applyAttachmentPlan(plan,now);
        UUID operationId=UUID.randomUUID(); operations.insert(operationId,recordId,request.sourceRevisionId(),actorId,version,after,currentNormalized.canonicalHash(),target.canonicalHash(),diff.summary(),key,payloadHash,now);
        List<String> changed=diff.sections().stream().filter(s->!"UNCHANGED".equals(s.status())).map(com.bionote.revision.RevisionDtos.DiffSection::key).toList();
        events.publish(new RecordRevisionRestoredEvent(UUID.randomUUID(),actorId,UUID.fromString(record.get("project_id").toString()),recordId,now,operationId,source.source().revisionNo(),request.sourceRevisionId(),version,after,changed,plan.dto().activate().size(),plan.dto().softDelete().size()));
        RestoreOperationRepository.Stored stored=operations.find(recordId,key);
        return new RestoreDtos.Result(records.get(actorId,recordId),stored.summary());
    }

    private void applyAttachmentPlan(AttachmentRestorePlanner.Plan plan, Instant now) {
        for(UUID id:plan.dto().activate()) jdbc.update("UPDATE attachments SET deleted_at=NULL WHERE id=?",id.toString());
        for(UUID id:plan.dto().softDelete()) jdbc.update("UPDATE attachments SET deleted_at=? WHERE id=? AND deleted_at IS NULL",Timestamp.from(now),id.toString());
    }
    private RestoreDtos.Result existing(UUID actorId, UUID recordId, String payloadHash, RestoreOperationRepository.Stored existing) {
        if(!existing.summary().actorId().equals(actorId)) throw new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","恢复操作不存在或无权访问");
        if(!existing.payloadHash().equals(payloadHash)) throw new ApiException(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","同一 Idempotency-Key 已用于不同恢复请求");
        return new RestoreDtos.Result(records.get(actorId,recordId),existing.summary());
    }
    private Map<String,Object> recordForUpdate(UUID recordId){List<Map<String,Object>> rows=jdbc.queryForList("SELECT r.*,p.status project_status FROM experiment_records r JOIN projects p ON p.id=r.project_id WHERE r.id=? AND r.deleted_at IS NULL FOR UPDATE",recordId.toString());if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","记录不存在或无权访问");return rows.get(0);}
    private void authorize(UUID actorId,Map<String,Object> record){List<String> roles=jdbc.queryForList("SELECT role FROM project_members WHERE project_id=? AND user_id=?",String.class,record.get("project_id"),actorId.toString());String role=roles.isEmpty()?null:roles.get(0);Decision d=policy.canRestore(new RecordContext(UUID.fromString(record.get("id").toString()),UUID.fromString(record.get("project_id").toString()),UUID.fromString(record.get("creator_id").toString()),record.get("status").toString(),record.get("project_status").toString(),false,Boolean.TRUE.equals(record.get("provisional")),role),actorId);if(!d.allowed())throw RestorePreviewService.policyException(d);}
    private void validateClaims(UUID actorId,UUID recordId,RestoreDtos.ExecuteRequest request,RestorePreviewTokenService.Claims c){if(!actorId.equals(c.actorId())||!recordId.equals(c.recordId())||!request.sourceRevisionId().equals(c.sourceRevisionId())||request.expectedRecordVersion()!=c.expectedRecordVersion()||request.restoreAttachments()!=c.restoreAttachments())throw new ApiException(HttpStatus.CONFLICT,"RESTORE_PREVIEW_STALE","恢复参数与预览不一致，请重新预览");}
    private String normalizeKey(String value){if(value==null||value.isBlank())throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","缺少 Idempotency-Key");try{return UUID.fromString(value.trim()).toString();}catch(Exception e){throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Idempotency-Key 必须是 UUID");}}
    private String payloadHash(UUID recordId,RestoreDtos.ExecuteRequest request){return sha256(recordId+"|"+request.sourceRevisionId()+"|"+request.expectedRecordVersion()+"|"+request.restoreAttachments());}
    private String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
