package com.bionote.agent.infrastructure.persistence;

import com.bionote.agent.report.ProjectProgressStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProjectProgressStore implements ProjectProgressStore {
    private final JdbcTemplate jdbc;

    public JdbcProjectProgressStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> findRole(UUID projectId, UUID actorId) {
        return jdbc
                .queryForList(
                        "SELECT role FROM project_members WHERE project_id=? AND user_id=?",
                        String.class,
                        projectId.toString(),
                        actorId.toString())
                .stream()
                .findFirst();
    }

    @Override
    public Optional<Map<String, Object>> findProjectOverview(UUID projectId) {
        return jdbc
                .queryForList(
                        "SELECT p.id,p.name,p.status,p.owner_id,u.display_name owner_name,p.created_at,p.updated_at FROM projects p JOIN users u ON u.id=p.owner_id WHERE p.id=?",
                        projectId.toString())
                .stream()
                .findFirst();
    }

    @Override
    public Map<String, Long> memberCounts(UUID projectId) {
        return counts(
                "SELECT role k,COUNT(*) c FROM project_members WHERE project_id=? GROUP BY role",
                projectId);
    }

    @Override
    public Map<String, Long> recordCounts(UUID projectId) {
        return counts(
                "SELECT status k,COUNT(*) c FROM experiment_records WHERE project_id=? AND deleted_at IS NULL AND provisional=FALSE GROUP BY status",
                projectId);
    }

    @Override
    public Map<String, Object> activityBounds(UUID projectId, Instant through) {
        return jdbc.queryForMap(
                "SELECT MIN(created_at) earliest,MAX(created_at) latest FROM audit_events WHERE project_id=? AND created_at<=?",
                projectId.toString(),
                Timestamp.from(through));
    }

    @Override
    public Map<String, Long> eventCounts(UUID projectId, Instant start, Instant end) {
        Map<String, Long> result = new LinkedHashMap<>();
        jdbc.query(
                "SELECT event_type k,COUNT(*) c FROM audit_events WHERE project_id=? AND created_at>=? AND created_at<=? GROUP BY event_type",
                (RowCallbackHandler) rs -> result.put(rs.getString("k"), rs.getLong("c")),
                projectId.toString(),
                Timestamp.from(start),
                Timestamp.from(end));
        return result;
    }

    @Override
    public List<Map<String, Object>> blockingRecords(UUID projectId, Instant staleBefore) {
        return jdbc.queryForList(
                "SELECT id,code,title,status,updated_at FROM experiment_records WHERE project_id=? AND deleted_at IS NULL AND provisional=FALSE AND (status IN ('CHANGES_REQUESTED','IN_REVIEW') OR (status='IN_PROGRESS' AND updated_at<?)) ORDER BY updated_at,id",
                projectId.toString(),
                Timestamp.from(staleBefore));
    }

    private Map<String, Long> counts(String sql, UUID projectId) {
        Map<String, Long> result = new LinkedHashMap<>();
        jdbc.query(
                sql,
                (RowCallbackHandler) rs -> result.put(rs.getString("k"), rs.getLong("c")),
                projectId.toString());
        return result;
    }
}
