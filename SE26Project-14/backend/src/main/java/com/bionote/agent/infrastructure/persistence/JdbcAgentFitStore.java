package com.bionote.agent.infrastructure.persistence;

import com.bionote.agent.fit.AgentFitStore;
import com.bionote.agent.fit.FitModels;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAgentFitStore implements AgentFitStore {
    private final JdbcTemplate jdbc;

    public JdbcAgentFitStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Map<String, Object>> findCatalogRecords(UUID projectId, int limit) {
        return jdbc.queryForList(
                """
                SELECT id, code, title, experiment_type, status, field_values_json
                FROM experiment_records
                WHERE project_id=? AND deleted_at IS NULL AND provisional=FALSE
                ORDER BY updated_at DESC LIMIT ?
                """,
                projectId.toString(),
                limit);
    }

    @Override
    public List<Map<String, Object>> findTabularAttachments(UUID recordId) {
        return jdbc.queryForList(
                """
                SELECT id, original_filename, storage_key, media_type, size_bytes FROM attachments
                WHERE record_id=? AND deleted_at IS NULL
                  AND (
                    LOWER(original_filename) LIKE '%.csv'
                    OR LOWER(original_filename) LIKE '%.xlsx'
                    OR media_type IN (
                      'text/csv','application/csv','text/plain',
                      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
                    )
                  )
                ORDER BY created_at DESC
                """,
                recordId.toString());
    }

    @Override
    public List<Map<String, Object>> findRecords(
            UUID projectId, FitModels.FitIntent intent, int limit) {
        StringBuilder sql =
                new StringBuilder(
                        """
                SELECT id, code, title, experiment_type, status, field_values_json, updated_at
                FROM experiment_records
                WHERE project_id=? AND deleted_at IS NULL AND provisional=FALSE
                """);
        List<Object> args = new ArrayList<>();
        args.add(projectId.toString());
        if (intent.recordCodes() != null && !intent.recordCodes().isEmpty()) {
            sql.append(" AND code IN (")
                    .append(placeholders(intent.recordCodes().size()))
                    .append(')');
            args.addAll(intent.recordCodes());
        }
        if (intent.statuses() != null && !intent.statuses().isEmpty()) {
            sql.append(" AND status IN (")
                    .append(placeholders(intent.statuses().size()))
                    .append(')');
            args.addAll(intent.statuses());
        }
        if (intent.experimentType() != null && !intent.experimentType().isBlank()) {
            sql.append(" AND experiment_type = ?");
            args.add(intent.experimentType());
        }
        if (intent.keyword() != null && !intent.keyword().isBlank()) {
            sql.append(" AND (LOWER(title) LIKE ? OR LOWER(code) LIKE ?)");
            String like = "%" + intent.keyword().toLowerCase() + "%";
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY updated_at DESC LIMIT ?");
        args.add(limit);
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    private String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }
}
