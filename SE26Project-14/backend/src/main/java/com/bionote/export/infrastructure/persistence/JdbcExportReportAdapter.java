package com.bionote.export.infrastructure.persistence;

import com.bionote.common.ApiException;
import com.bionote.export.ExportReportStore;
import com.bionote.export.RecordReportModel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcExportReportAdapter implements ExportReportStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcExportReportAdapter(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public LoadedReport loadCompleted(UUID user, UUID id) {
        List<Map<String, Object>> access =
                jdbc.queryForList(
                        "SELECT r.status FROM experiment_records r JOIN project_members pm ON pm.project_id=r.project_id AND pm.user_id=? WHERE r.id=? AND r.deleted_at IS NULL",
                        user.toString(),
                        id.toString());
        if (access.isEmpty())
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "记录不存在或无权访问");
        if (!"COMPLETED".equals(access.get(0).get("status")))
            throw new ApiException(HttpStatus.CONFLICT, "EXPORT_NOT_AVAILABLE", "只有已完成记录可以导出");
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        "SELECT r.*,p.name project_name,u.display_name creator_name,rv.revision_no,rv.snapshot_json,v.decision_comment,v.decided_at,reviewer.display_name reviewer_name FROM experiment_records r JOIN projects p ON p.id=r.project_id JOIN project_members pm ON pm.project_id=r.project_id AND pm.user_id=? JOIN users u ON u.id=r.creator_id JOIN record_revisions rv ON rv.id=r.final_revision_id JOIN reviews v ON v.revision_id=rv.id AND v.status='APPROVED' JOIN users reviewer ON reviewer.id=v.reviewer_id WHERE r.id=? AND r.deleted_at IS NULL AND r.status='COMPLETED'",
                        user.toString(),
                        id.toString());
        if (rows.isEmpty())
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "INVALID_FINAL_REVISION", "最终批准修订数据不完整");
        Map<String, Object> r = rows.get(0),
                s = decodeMap(r.get("snapshot_json").toString()),
                template = asMap(s.get("templateSnapshot")),
                values = asMap(s.get("fieldValues"));
        List<RecordReportModel.Field> fields = new ArrayList<>();
        Object fieldData = template.get("fields");
        if (fieldData instanceof List<?> list)
            for (Object item : list) {
                Map<String, Object> f = asMap(item);
                String key = Objects.toString(f.get("fieldKey"), "");
                fields.add(
                        new RecordReportModel.Field(
                                Objects.toString(f.get("label"), key), display(values.get(key))));
            }
        List<RecordReportModel.Attachment> attachments =
                jdbc.query(
                        "SELECT a.original_filename,a.size_bytes,u.display_name,a.created_at FROM revision_attachments ra JOIN attachments a ON a.id=ra.attachment_id JOIN users u ON u.id=a.uploader_id WHERE ra.revision_id=? ORDER BY ra.sort_order",
                        (rs, n) ->
                                new RecordReportModel.Attachment(
                                        rs.getString("original_filename"),
                                        rs.getLong("size_bytes"),
                                        rs.getString("display_name"),
                                        rs.getTimestamp("created_at").toInstant()),
                        r.get("final_revision_id"));
        RecordReportModel model =
                new RecordReportModel(
                        Objects.toString(s.get("title")),
                        Objects.toString(s.get("code")),
                        r.get("project_name").toString(),
                        Objects.toString(s.get("experimentType")),
                        LocalDate.parse(Objects.toString(s.get("experimentDate"))),
                        r.get("creator_name").toString(),
                        Objects.toString(s.get("purpose")),
                        fields,
                        Objects.toString(s.get("contentHtml"), ""),
                        Objects.toString(s.get("contentPlainText"), ""),
                        ((Number) r.get("revision_no")).intValue(),
                        r.get("reviewer_name").toString(),
                        ((Timestamp) r.get("decided_at")).toInstant(),
                        Objects.toString(r.get("decision_comment"), null),
                        attachments);
        return new LoadedReport(UUID.fromString(r.get("project_id").toString()), model);
    }

    private Map<String, Object> decodeMap(String value) {
        try {
            return json.readValue(value, new TypeReference<>() {});
        } catch (Exception e) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "INVALID_REVISION_SNAPSHOT", "修订快照损坏");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

    private String display(Object value) {
        if (value == null) return "—";
        if (value instanceof Collection<?> collection)
            return String.join("、", collection.stream().map(Object::toString).toList());
        return value.toString();
    }
}
