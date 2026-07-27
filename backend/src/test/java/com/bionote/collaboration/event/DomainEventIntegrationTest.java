package com.bionote.collaboration.event;

import com.bionote.user.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DomainEventIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired DomainEventPublisher publisher;
    @Autowired ObjectMapper json;

    @BeforeEach
    void clean() {
        jdbc.update("UPDATE experiment_records SET current_review_id=NULL,final_revision_id=NULL");
        jdbc.update("DELETE FROM revision_attachments"); jdbc.update("DELETE FROM reviews"); jdbc.update("DELETE FROM record_revisions");
        jdbc.update("DELETE FROM attachments"); jdbc.update("DELETE FROM experiment_records"); jdbc.update("DELETE FROM audit_events");
        jdbc.update("DELETE FROM notifications"); jdbc.update("DELETE FROM project_invitations"); jdbc.update("DELETE FROM project_members"); jdbc.update("DELETE FROM projects"); users.deleteAll();
    }

    @Test
    void restoredEventWritesIdempotentSanitizedAuditRow() throws Exception {
        UUID actor = UUID.randomUUID(), project = UUID.randomUUID(), record = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO users(id,display_name,email_normalized,password_hash,created_at,updated_at,version) VALUES(?,?,?,?,?,?,0)", actor.toString(), "事件用户", "event@example.com", "hash", Timestamp.from(now), Timestamp.from(now));
        jdbc.update("INSERT INTO projects(id,name,status,owner_id,created_at,updated_at,version) VALUES(?,?,'ACTIVE',?,?,?,0)", project.toString(), "事件项目", actor.toString(), Timestamp.from(now), Timestamp.from(now));
        jdbc.update("INSERT INTO project_members(project_id,user_id,role,joined_at) VALUES(?,?, 'OWNER',?)", project.toString(), actor.toString(), Timestamp.from(now));
        UUID eventId = UUID.randomUUID(), operationId = UUID.randomUUID(), revisionId = UUID.randomUUID();
        RecordRevisionRestoredEvent event = new RecordRevisionRestoredEvent(eventId, actor, project, record, now,
                operationId, 2, revisionId, 4, 5, List.of("SCALAR", "ATTACHMENT_SET"), 1, 2);
        publisher.publish(event);
        publisher.publish(event);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE id=?", Long.class, eventId.toString())).isEqualTo(1);
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM audit_events WHERE id=?", eventId.toString());
        assertThat(row.get("event_type")).isEqualTo("RECORD_REVISION_RESTORED");
        assertThat(row.get("target_type")).isEqualTo("RESTORE_OPERATION");
        assertThat(row.get("target_id")).isEqualTo(operationId.toString());
        Map<String, Object> metadata = json.readValue(row.get("metadata_json").toString(), new TypeReference<>() {});
        assertThat(metadata).containsEntry("revisionNo", 2).containsEntry("sourceRevisionId", revisionId.toString())
                .containsEntry("fromVersion", 4).containsEntry("toVersion", 5)
                .containsEntry("attachmentAdded", 1).containsEntry("attachmentRemoved", 2);
        assertThat(metadata).doesNotContainKeys("content", "prompt", "token", "storageKey", "previewToken");
    }
}
