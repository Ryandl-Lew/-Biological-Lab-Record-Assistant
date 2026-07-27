package com.bionote.collaboration.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class AuditEventWriter {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AuditEventWriter(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    public void append(UUID eventId, UUID actorId, UUID projectId, UUID recordId, String eventType,
                       String targetType, UUID targetId, Map<String, ?> metadata, Instant occurredAt) {
        jdbc.update("INSERT INTO audit_events(id,actor_id,project_id,record_id,event_type,target_type,target_id,metadata_json,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                eventId.toString(), value(actorId), value(projectId), value(recordId), eventType, targetType,
                targetId.toString(), encode(metadata), Timestamp.from(occurredAt));
    }

    public void appendOnce(UUID eventId, UUID actorId, UUID projectId, UUID recordId, String eventType,
                           String targetType, UUID targetId, Map<String, ?> metadata, Instant occurredAt) {
        try {
            append(eventId, actorId, projectId, recordId, eventType, targetType, targetId, metadata, occurredAt);
        } catch (DuplicateKeyException duplicate) {
            Integer matching = jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE id=? AND event_type=? AND target_type=? AND target_id=?",
                    Integer.class, eventId.toString(), eventType, targetType, targetId.toString());
            if (matching == null || matching != 1) throw duplicate;
        }
    }

    private String encode(Map<String, ?> value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("Audit metadata cannot be encoded", exception); }
    }
    private String value(UUID id) { return id == null ? null : id.toString(); }
}
