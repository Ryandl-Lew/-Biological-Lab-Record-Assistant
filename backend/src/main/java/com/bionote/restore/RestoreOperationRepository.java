package com.bionote.restore;

import com.bionote.common.ApiException;
import com.bionote.common.PagedResponse;
import com.bionote.revision.RevisionDtos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class RestoreOperationRepository {
    private final JdbcTemplate jdbc; private final ObjectMapper json;
    public RestoreOperationRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    public Stored find(UUID recordId, String key) {
        List<Stored> rows = jdbc.query("SELECT o.*,rv.revision_no,u.display_name actor_name FROM record_restore_operations o JOIN record_revisions rv ON rv.id=o.source_revision_id JOIN users u ON u.id=o.actor_id WHERE o.record_id=? AND o.idempotency_key=?",
                (rs, n) -> stored(rs.getString("id"), rs.getString("record_id"), rs.getString("source_revision_id"), rs.getInt("revision_no"), rs.getString("actor_id"), rs.getString("actor_name"),
                        rs.getLong("before_record_version"), rs.getLong("after_record_version"), rs.getString("before_content_hash"), rs.getString("after_content_hash"),
                        rs.getString("diff_summary_json"), rs.getString("payload_hash"), rs.getTimestamp("restored_at")), recordId.toString(), key);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void insert(UUID id, UUID recordId, UUID sourceRevisionId, UUID actorId,
                       long beforeVersion, long afterVersion, String beforeHash, String afterHash,
                       RevisionDtos.DiffSummary summary, String key, String payloadHash, Instant restoredAt) {
        jdbc.update("INSERT INTO record_restore_operations(id,record_id,source_revision_id,actor_id,before_record_version,after_record_version,before_content_hash,after_content_hash,diff_summary_json,idempotency_key,payload_hash,restored_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                id.toString(), recordId.toString(), sourceRevisionId.toString(), actorId.toString(), beforeVersion, afterVersion,
                beforeHash, afterHash, encode(summary), key, payloadHash, Timestamp.from(restoredAt));
    }

    public PagedResponse<RestoreDtos.OperationSummary> list(UUID actorId, UUID recordId, int page, int size) {
        requireMember(actorId, recordId); page = Math.max(0, page); size = Math.max(1, Math.min(100, size));
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM record_restore_operations WHERE record_id=?", Long.class, recordId.toString());
        List<RestoreDtos.OperationSummary> items = jdbc.query("SELECT o.*,rv.revision_no,u.display_name actor_name FROM record_restore_operations o JOIN record_revisions rv ON rv.id=o.source_revision_id JOIN users u ON u.id=o.actor_id WHERE o.record_id=? ORDER BY o.restored_at DESC,o.id DESC LIMIT ? OFFSET ?",
                (rs, n) -> stored(rs.getString("id"), rs.getString("record_id"), rs.getString("source_revision_id"), rs.getInt("revision_no"), rs.getString("actor_id"), rs.getString("actor_name"),
                        rs.getLong("before_record_version"), rs.getLong("after_record_version"), rs.getString("before_content_hash"), rs.getString("after_content_hash"),
                        rs.getString("diff_summary_json"), rs.getString("payload_hash"), rs.getTimestamp("restored_at")).summary(), recordId.toString(), size, page * size);
        return PagedResponse.of(items, page, size, total);
    }

    private void requireMember(UUID actorId, UUID recordId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM experiment_records r JOIN project_members pm ON pm.project_id=r.project_id AND pm.user_id=? WHERE r.id=? AND r.deleted_at IS NULL", Integer.class, actorId.toString(), recordId.toString());
        if (count == null || count == 0) throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "记录不存在或无权访问");
    }
    private Stored stored(String id, String recordId, String revisionId, int revisionNo, String actorId, String actorName,
                          long beforeVersion, long afterVersion, String beforeHash, String afterHash,
                          String summaryJson, String payloadHash, Timestamp restoredAt) {
        RestoreDtos.OperationSummary summary = new RestoreDtos.OperationSummary(UUID.fromString(id), UUID.fromString(recordId), UUID.fromString(revisionId), revisionNo,
                UUID.fromString(actorId), actorName, beforeVersion, afterVersion, beforeHash, afterHash, decode(summaryJson), restoredAt.toInstant());
        return new Stored(summary, payloadHash);
    }
    private String encode(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalArgumentException(e); } }
    private RevisionDtos.DiffSummary decode(String value) { try { return json.readValue(value, RevisionDtos.DiffSummary.class); } catch (Exception e) { throw new IllegalStateException("Invalid restore diff summary", e); } }
    public record Stored(RestoreDtos.OperationSummary summary, String payloadHash) {}
}
