package com.bionote.revision.infrastructure.persistence;

import com.bionote.common.ApiException;
import com.bionote.common.PagedResponse;
import com.bionote.revision.RevisionDtos;
import com.bionote.revision.RevisionStore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRevisionQueryAdapter implements RevisionStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcRevisionQueryAdapter(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public PagedResponse<RevisionDtos.RevisionSummary> list(
            UUID actorId, UUID recordId, int page, int size) {
        requireRecordMember(actorId, recordId);
        page = Math.max(0, page);
        size = Math.max(1, Math.min(100, size));
        long total =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM record_revisions WHERE record_id=?",
                        Long.class,
                        recordId.toString());
        String sql =
                "SELECT rv.id,rv.record_id,rv.revision_no,rv.submitted_by,su.display_name submitter_name,"
                        + "rv.submitted_at,rv.submit_note,rv.content_hash,v.id review_id,v.reviewer_id,ru.display_name reviewer_name,"
                        + "v.status review_status,v.decision_comment,v.assigned_at,v.decided_at,"
                        + "(SELECT COUNT(*) FROM revision_attachments ra WHERE ra.revision_id=rv.id) attachment_count,"
                        + "CASE WHEN r.current_review_id=v.id THEN TRUE ELSE FALSE END is_current,"
                        + "CASE WHEN r.final_revision_id=rv.id THEN TRUE ELSE FALSE END is_final "
                        + "FROM record_revisions rv JOIN experiment_records r ON r.id=rv.record_id "
                        + "JOIN users su ON su.id=rv.submitted_by JOIN reviews v ON v.revision_id=rv.id "
                        + "JOIN users ru ON ru.id=v.reviewer_id WHERE rv.record_id=? "
                        + "ORDER BY rv.revision_no DESC,rv.id DESC LIMIT ? OFFSET ?";
        List<RevisionDtos.RevisionSummary> data =
                jdbc.query(
                        sql,
                        (rs, rowNum) ->
                                new RevisionDtos.RevisionSummary(
                                        uuid(rs.getString("id")),
                                        uuid(rs.getString("record_id")),
                                        rs.getInt("revision_no"),
                                        "R" + rs.getInt("revision_no"),
                                        uuid(rs.getString("submitted_by")),
                                        rs.getString("submitter_name"),
                                        rs.getTimestamp("submitted_at").toInstant(),
                                        rs.getString("submit_note"),
                                        rs.getString("content_hash"),
                                        new RevisionDtos.ReviewSummary(
                                                uuid(rs.getString("review_id")),
                                                uuid(rs.getString("reviewer_id")),
                                                rs.getString("reviewer_name"),
                                                rs.getString("review_status"),
                                                rs.getString("decision_comment"),
                                                rs.getTimestamp("assigned_at").toInstant(),
                                                instant(rs.getTimestamp("decided_at")),
                                                actorId.toString()
                                                                .equals(rs.getString("reviewer_id"))
                                                        && "PENDING"
                                                                .equals(
                                                                        rs.getString(
                                                                                "review_status"))),
                                        rs.getInt("attachment_count"),
                                        rs.getBoolean("is_current"),
                                        rs.getBoolean("is_final")),
                        recordId.toString(),
                        size,
                        page * size);
        return PagedResponse.of(data, page, size, total);
    }

    public RevisionDtos.RevisionDetail detail(UUID actorId, UUID recordId, UUID revisionId) {
        String pathClause = recordId == null ? "" : " AND rv.record_id=?";
        List<Object> args = new ArrayList<>();
        args.add(revisionId.toString());
        args.add(actorId.toString());
        if (recordId != null) args.add(recordId.toString());
        String sql =
                "SELECT rv.*,su.display_name submitter_name,v.id review_id,v.reviewer_id,ru.display_name reviewer_name,"
                        + "v.status review_status,v.decision_comment,v.assigned_at,v.decided_at,r.current_review_id,r.final_revision_id "
                        + "FROM record_revisions rv JOIN experiment_records r ON r.id=rv.record_id "
                        + "JOIN project_members pm ON pm.project_id=r.project_id AND pm.user_id=? "
                        + "JOIN users su ON su.id=rv.submitted_by JOIN reviews v ON v.revision_id=rv.id "
                        + "JOIN users ru ON ru.id=v.reviewer_id WHERE rv.id=? AND r.deleted_at IS NULL"
                        + pathClause;
        // SQL parameter order follows join member first, then revision, then optional record.
        args.clear();
        args.add(actorId.toString());
        args.add(revisionId.toString());
        if (recordId != null) args.add(recordId.toString());
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args.toArray());
        if (rows.isEmpty())
            throw new ApiException(HttpStatus.NOT_FOUND, "REVISION_NOT_FOUND", "修订不存在或无权访问");
        Map<String, Object> row = rows.get(0);
        UUID resolvedRecordId = uuid(row.get("record_id").toString());
        Map<String, Object> snapshot = decodeMap(row.get("snapshot_json").toString());
        RevisionDtos.SnapshotView snapshotView = snapshotView(resolvedRecordId, snapshot);
        UUID reviewId = uuid(row.get("review_id").toString());
        RevisionDtos.ReviewSummary review =
                new RevisionDtos.ReviewSummary(
                        reviewId,
                        uuid(row.get("reviewer_id").toString()),
                        row.get("reviewer_name").toString(),
                        row.get("review_status").toString(),
                        Objects.toString(row.get("decision_comment"), null),
                        ((Timestamp) row.get("assigned_at")).toInstant(),
                        row.get("decided_at") == null
                                ? null
                                : ((Timestamp) row.get("decided_at")).toInstant(),
                        actorId.toString().equals(row.get("reviewer_id").toString())
                                && "PENDING".equals(row.get("review_status").toString()));
        List<RevisionDtos.AttachmentView> attachments = revisionAttachments(revisionId);
        int revisionNo = ((Number) row.get("revision_no")).intValue();
        return new RevisionDtos.RevisionDetail(
                revisionId,
                resolvedRecordId,
                revisionNo,
                "R" + revisionNo,
                ((Number) row.get("snapshot_schema_version")).intValue(),
                snapshotView,
                row.get("content_hash").toString(),
                uuid(row.get("submitted_by").toString()),
                row.get("submitter_name").toString(),
                ((Timestamp) row.get("submitted_at")).toInstant(),
                Objects.toString(row.get("submit_note"), null),
                review,
                attachments,
                reviewId.toString().equals(Objects.toString(row.get("current_review_id"), "")),
                revisionId.toString().equals(Objects.toString(row.get("final_revision_id"), "")));
    }

    public SourceRecord revisionSource(UUID actorId, UUID recordId, UUID revisionId) {
        RevisionDtos.RevisionDetail detail = detail(actorId, recordId, revisionId);
        Map<String, Object> raw =
                jdbc.queryForObject(
                        "SELECT snapshot_json FROM record_revisions WHERE id=?",
                        (rs, n) -> decodeMap(rs.getString(1)),
                        revisionId.toString());
        return new SourceRecord(
                new RevisionDtos.SnapshotSourceRef(
                        "REVISION", revisionId, detail.revisionNo(), null, detail.contentHash()),
                detail.snapshotSchemaVersion(),
                raw,
                detail.attachments(),
                detail.review(),
                detail.submitNote(),
                null);
    }

    public SourceRecord workingCopySource(UUID actorId, UUID recordId) {
        String sql =
                "SELECT r.* FROM experiment_records r JOIN project_members pm ON pm.project_id=r.project_id AND pm.user_id=? "
                        + "WHERE r.id=? AND r.deleted_at IS NULL";
        List<Map<String, Object>> rows =
                jdbc.queryForList(sql, actorId.toString(), recordId.toString());
        if (rows.isEmpty())
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "记录不存在或无权访问");
        Map<String, Object> row = rows.get(0), snapshot = new LinkedHashMap<>();
        snapshot.put("id", row.get("id").toString());
        snapshot.put("code", row.get("code"));
        snapshot.put("projectId", row.get("project_id").toString());
        snapshot.put("creatorId", row.get("creator_id").toString());
        snapshot.put("title", row.get("title"));
        snapshot.put("experimentType", row.get("experiment_type"));
        snapshot.put("experimentDate", row.get("experiment_date").toString());
        snapshot.put("purpose", row.get("purpose"));
        snapshot.put("templateSnapshot", decode(row.get("template_snapshot_json").toString()));
        snapshot.put("fieldValues", decode(row.get("field_values_json").toString()));
        snapshot.put("contentJson", decode(row.get("content_json").toString()));
        snapshot.put("contentPlainText", row.get("content_plain_text"));
        List<RevisionDtos.AttachmentView> attachments = activeAttachments(recordId);
        long version = ((Number) row.get("version")).longValue();
        return new SourceRecord(
                new RevisionDtos.SnapshotSourceRef("WORKING_COPY", null, null, version, null),
                1,
                snapshot,
                attachments,
                null,
                null,
                version);
    }

    private List<RevisionDtos.AttachmentView> revisionAttachments(UUID revisionId) {
        return jdbc.query(
                "SELECT a.id,a.original_filename,a.media_type,a.size_bytes,a.uploader_id,u.display_name uploader_name,a.created_at "
                        + "FROM revision_attachments ra JOIN attachments a ON a.id=ra.attachment_id JOIN users u ON u.id=a.uploader_id "
                        + "WHERE ra.revision_id=? ORDER BY ra.sort_order,a.id",
                (rs, n) ->
                        attachment(
                                rs.getString("id"),
                                rs.getString("original_filename"),
                                rs.getString("media_type"),
                                rs.getLong("size_bytes"),
                                rs.getString("uploader_id"),
                                rs.getString("uploader_name"),
                                rs.getTimestamp("created_at")),
                revisionId.toString());
    }

    private List<RevisionDtos.AttachmentView> activeAttachments(UUID recordId) {
        return jdbc.query(
                "SELECT a.id,a.original_filename,a.media_type,a.size_bytes,a.uploader_id,u.display_name uploader_name,a.created_at "
                        + "FROM attachments a JOIN users u ON u.id=a.uploader_id WHERE a.record_id=? AND a.deleted_at IS NULL ORDER BY a.created_at,a.id",
                (rs, n) ->
                        attachment(
                                rs.getString("id"),
                                rs.getString("original_filename"),
                                rs.getString("media_type"),
                                rs.getLong("size_bytes"),
                                rs.getString("uploader_id"),
                                rs.getString("uploader_name"),
                                rs.getTimestamp("created_at")),
                recordId.toString());
    }

    private RevisionDtos.AttachmentView attachment(
            String id,
            String name,
            String mediaType,
            long size,
            String uploaderId,
            String uploaderName,
            Timestamp createdAt) {
        return new RevisionDtos.AttachmentView(
                uuid(id),
                name,
                mediaType,
                size,
                uuid(uploaderId),
                uploaderName,
                createdAt.toInstant());
    }

    private void requireRecordMember(UUID actorId, UUID recordId) {
        Integer count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM experiment_records r JOIN project_members pm ON pm.project_id=r.project_id "
                                + "WHERE r.id=? AND r.deleted_at IS NULL AND pm.user_id=?",
                        Integer.class,
                        recordId.toString(),
                        actorId.toString());
        if (count == null || count == 0)
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "记录不存在或无权访问");
    }

    private RevisionDtos.SnapshotView snapshotView(UUID recordId, Map<String, Object> snapshot) {
        return new RevisionDtos.SnapshotView(
                recordId,
                Objects.toString(snapshot.get("code"), ""),
                parseUuid(snapshot.get("projectId")),
                parseUuid(snapshot.get("creatorId")),
                Objects.toString(snapshot.get("title"), ""),
                Objects.toString(snapshot.get("experimentType"), ""),
                Objects.toString(snapshot.get("experimentDate"), ""),
                Objects.toString(snapshot.get("purpose"), ""),
                snapshot.getOrDefault("templateSnapshot", Map.of()),
                snapshot.getOrDefault("fieldValues", Map.of()),
                snapshot.getOrDefault("contentJson", Map.of()),
                Objects.toString(snapshot.get("contentPlainText"), ""));
    }

    private Map<String, Object> decodeMap(String value) {
        try {
            return json.readValue(value, new TypeReference<>() {});
        } catch (Exception e) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "REVISION_SNAPSHOT_INVALID", "修订快照无法解析");
        }
    }

    private Object decode(String value) {
        try {
            return json.readValue(value, Object.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private UUID parseUuid(Object value) {
        try {
            return value == null ? null : UUID.fromString(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private UUID uuid(String value) {
        return UUID.fromString(value);
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
