package com.bionote.agent.infrastructure.persistence;

import com.bionote.agent.api.AgentDtos;
import com.bionote.agent.api.ChatSessionStore;
import com.bionote.common.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatSessionService implements ChatSessionStore {
    private final JdbcTemplate jdbc;

    public ChatSessionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AgentDtos.ChatSessionSummary> listByProject(UUID userId, UUID projectId) {
        return jdbc
                .queryForList(
                        "SELECT s.id, s.title, s.created_at, s.updated_at, (SELECT COUNT(*) FROM agent_chat_messages m WHERE m.session_id = s.id) AS msg_count FROM agent_chat_sessions s WHERE s.user_id = ? AND s.project_id = ? ORDER BY s.updated_at DESC LIMIT 20",
                        userId.toString(),
                        projectId.toString())
                .stream()
                .map(
                        row ->
                                new AgentDtos.ChatSessionSummary(
                                        UUID.fromString(row.get("id").toString()),
                                        String.valueOf(row.get("title")),
                                        ((java.sql.Timestamp) row.get("created_at")).toInstant(),
                                        ((java.sql.Timestamp) row.get("updated_at")).toInstant(),
                                        ((Number) row.get("msg_count")).intValue()))
                .toList();
    }

    public List<AgentDtos.ChatSessionSummary> listByRecord(UUID userId, UUID recordId) {
        return jdbc
                .queryForList(
                        "SELECT s.id, s.title, s.created_at, s.updated_at, (SELECT COUNT(*) FROM agent_chat_messages m WHERE m.session_id = s.id) AS msg_count FROM agent_chat_sessions s WHERE s.user_id = ? AND s.record_id = ? ORDER BY s.updated_at DESC LIMIT 20",
                        userId.toString(),
                        recordId.toString())
                .stream()
                .map(
                        row ->
                                new AgentDtos.ChatSessionSummary(
                                        UUID.fromString(row.get("id").toString()),
                                        String.valueOf(row.get("title")),
                                        ((java.sql.Timestamp) row.get("created_at")).toInstant(),
                                        ((java.sql.Timestamp) row.get("updated_at")).toInstant(),
                                        ((Number) row.get("msg_count")).intValue()))
                .toList();
    }

    public AgentDtos.ChatSessionDetail get(UUID userId, UUID sessionId) {
        Map<String, Object> session =
                jdbc.queryForMap(
                        "SELECT id, project_id, record_id, title, created_at, updated_at FROM agent_chat_sessions WHERE id = ? AND user_id = ?",
                        sessionId.toString(),
                        userId.toString());
        List<Map<String, Object>> msgRows =
                jdbc.queryForList(
                        "SELECT role, content, metadata FROM agent_chat_messages WHERE session_id = ? ORDER BY created_at ASC",
                        sessionId.toString());
        List<AgentDtos.ChatMessage> messages =
                msgRows.stream()
                        .map(
                                row ->
                                        new AgentDtos.ChatMessage(
                                                String.valueOf(row.get("role")),
                                                String.valueOf(row.get("content")),
                                                nullable(row.get("metadata"))))
                        .toList();
        return new AgentDtos.ChatSessionDetail(
                UUID.fromString(session.get("id").toString()),
                session.get("project_id") != null
                        ? UUID.fromString(session.get("project_id").toString())
                        : null,
                session.get("record_id") != null
                        ? UUID.fromString(session.get("record_id").toString())
                        : null,
                String.valueOf(session.get("title")),
                messages,
                ((java.sql.Timestamp) session.get("created_at")).toInstant(),
                ((java.sql.Timestamp) session.get("updated_at")).toInstant());
    }

    @Transactional
    public AgentDtos.ChatSessionDetail save(
            UUID userId, UUID projectId, UUID recordId, AgentDtos.SaveSessionRequest request) {
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        String title =
                request.title() != null && !request.title().isBlank() ? request.title() : "新对话";
        jdbc.update(
                "INSERT INTO agent_chat_sessions (id, project_id, record_id, user_id, title, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                sessionId.toString(),
                projectId != null ? projectId.toString() : null,
                recordId != null ? recordId.toString() : null,
                userId.toString(),
                title,
                now,
                now);
        if (request.messages() != null) {
            for (AgentDtos.ChatMessage msg : request.messages()) {
                jdbc.update(
                        "INSERT INTO agent_chat_messages (id, session_id, role, content, metadata, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID().toString(),
                        sessionId.toString(),
                        msg.role(),
                        msg.content(),
                        msg.metadata(),
                        now);
            }
        }
        return get(userId, sessionId);
    }

    @Override
    @Transactional
    public AgentDtos.ChatSessionDetail appendMessages(
            UUID userId, UUID sessionId, List<AgentDtos.ChatMessage> messages) {
        Map<String, Object> existing =
                jdbc.queryForMap(
                        "SELECT id FROM agent_chat_sessions WHERE id = ? AND user_id = ?",
                        sessionId.toString(),
                        userId.toString());
        addMessages(sessionId, messages);
        return get(userId, sessionId);
    }

    @Transactional
    public void addMessages(UUID sessionId, List<AgentDtos.ChatMessage> messages) {
        for (AgentDtos.ChatMessage msg : messages) {
            jdbc.update(
                    "INSERT INTO agent_chat_messages (id, session_id, role, content, metadata, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID().toString(),
                    sessionId.toString(),
                    msg.role(),
                    msg.content(),
                    msg.metadata(),
                    Instant.now());
        }
        jdbc.update(
                "UPDATE agent_chat_sessions SET updated_at = ? WHERE id = ?",
                Instant.now(),
                sessionId.toString());
    }

    public void delete(UUID userId, UUID sessionId) {
        jdbc.update("DELETE FROM agent_chat_messages WHERE session_id = ?", sessionId.toString());
        int deleted =
                jdbc.update(
                        "DELETE FROM agent_chat_sessions WHERE id = ? AND user_id = ?",
                        sessionId.toString(),
                        userId.toString());
        if (deleted == 0)
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "对话不存在");
    }

    private static String nullable(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
