package com.bionote.audit.infrastructure.persistence;

import com.bionote.audit.AuditQueryStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAuditQueryAdapter implements AuditQueryStore {
    private static final String TIMELINE_SQL =
            "'PROJECT_CREATED','PROJECT_ARCHIVED','INVITATION_CREATED','INVITATION_ACCEPTED','INVITATION_REJECTED','INVITATION_EXPIRED','MEMBER_ROLE_CHANGED','MEMBER_REMOVED','RECORD_CREATED','RECORD_DELETED','ATTACHMENT_UPLOADED','ATTACHMENT_DELETED','RECORD_SUBMITTED','REVIEW_CHANGES_REQUESTED','REVIEW_APPROVED','REVIEWER_REASSIGNED','RECORD_EXPORT_PREVIEW','RECORD_EXPORT_MARKDOWN','RECORD_EXPORT_PDF','RECORD_REVISION_RESTORED','AGENT_RUN_SUCCEEDED'";
    private final JdbcTemplate jdbc;

    public JdbcAuditQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean exists(UUID eventId, UUID projectId) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT COUNT(*)>0 FROM audit_events WHERE id=? AND project_id=?",
                        Boolean.class,
                        eventId.toString(),
                        projectId.toString()));
    }

    @Override
    public EventPage findEvents(
            UUID projectId,
            String eventType,
            UUID actorId,
            Instant from,
            Instant to,
            int page,
            int size) {
        String where =
                " FROM audit_events a LEFT JOIN users u ON u.id=a.actor_id WHERE a.project_id=? AND a.event_type IN ("
                        + TIMELINE_SQL
                        + ") AND (? IS NULL OR a.event_type=?) AND (? IS NULL OR a.actor_id=?) AND (? IS NULL OR a.created_at>=?) AND (? IS NULL OR a.created_at<?)";
        Timestamp start = from == null ? null : Timestamp.from(from),
                end = to == null ? null : Timestamp.from(to);
        List<Object> args =
                Arrays.asList(
                        projectId.toString(),
                        eventType,
                        eventType,
                        value(actorId),
                        value(actorId),
                        start,
                        start,
                        end,
                        end);
        long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
        List<Object> paged = new ArrayList<>(args);
        paged.add(size);
        paged.add(page * size);
        List<EventRecord> items =
                jdbc.query(
                        "SELECT a.*,u.display_name actor_name"
                                + where
                                + " ORDER BY a.created_at DESC,a.id DESC LIMIT ? OFFSET ?",
                        (rs, n) ->
                                new EventRecord(
                                        UUID.fromString(rs.getString("id")),
                                        rs.getString("event_type"),
                                        rs.getString("target_type"),
                                        UUID.fromString(rs.getString("target_id")),
                                        uuid(rs.getString("record_id")),
                                        uuid(rs.getString("actor_id")),
                                        rs.getString("actor_name") == null
                                                ? "系统"
                                                : rs.getString("actor_name"),
                                        rs.getString("metadata_json"),
                                        rs.getTimestamp("created_at").toInstant()),
                        paged.toArray());
        return new EventPage(items, total);
    }

    @Override
    public AttachmentPage findAttachments(UUID projectId, int page, int size) {
        long total =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM attachments a JOIN experiment_records r ON r.id=a.record_id WHERE r.project_id=? AND r.deleted_at IS NULL AND r.provisional=FALSE AND a.deleted_at IS NULL",
                        Long.class,
                        projectId.toString());
        List<AttachmentRecord> items =
                jdbc.query(
                        "SELECT a.id,a.original_filename,a.media_type,a.size_bytes,a.created_at,r.id record_id,r.title,r.code,u.display_name FROM attachments a JOIN experiment_records r ON r.id=a.record_id JOIN users u ON u.id=a.uploader_id WHERE r.project_id=? AND r.deleted_at IS NULL AND r.provisional=FALSE AND a.deleted_at IS NULL ORDER BY a.created_at DESC,a.id DESC LIMIT ? OFFSET ?",
                        (rs, n) ->
                                new AttachmentRecord(
                                        UUID.fromString(rs.getString("id")),
                                        rs.getString("original_filename"),
                                        rs.getString("media_type"),
                                        rs.getLong("size_bytes"),
                                        UUID.fromString(rs.getString("record_id")),
                                        rs.getString("title"),
                                        rs.getString("code"),
                                        rs.getString("display_name"),
                                        rs.getTimestamp("created_at").toInstant()),
                        projectId.toString(),
                        size,
                        page * size);
        return new AttachmentPage(items, total);
    }

    private String value(UUID id) {
        return id == null ? null : id.toString();
    }

    private UUID uuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }
}
