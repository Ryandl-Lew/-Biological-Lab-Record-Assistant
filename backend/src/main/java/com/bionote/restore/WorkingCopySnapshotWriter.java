package com.bionote.restore;

import com.bionote.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
public class WorkingCopySnapshotWriter {
    private static final Safelist CONTENT = Safelist.relaxed().addTags("h2","h3","blockquote","hr","pre","code","s","strike").addAttributes("a","target","rel");
    private final JdbcTemplate jdbc; private final ObjectMapper json;
    public WorkingCopySnapshotWriter(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    public Prepared prepare(Map<String, Object> record, Map<String, Object> snapshot, Set<UUID> allowedFileIds) {
        identity(record, snapshot, "id", "id"); identity(record, snapshot, "code", "code");
        identity(record, snapshot, "project_id", "projectId"); identity(record, snapshot, "creator_id", "creatorId");
        Object template = snapshot.getOrDefault("templateSnapshot", Map.of());
        Map<String, Object> values = map(snapshot.get("fieldValues"));
        filterFileFields(template, values, allowedFileIds);
        String clean = Jsoup.clean(Objects.toString(snapshot.get("contentHtml"), ""), CONTENT);
        return new Prepared(Objects.toString(snapshot.get("title"), "").trim(), Objects.toString(snapshot.get("experimentType"), "").trim(),
                LocalDate.parse(Objects.toString(snapshot.get("experimentDate"))), Objects.toString(snapshot.get("purpose"), "").trim(),
                encode(template), encode(values), encode(snapshot.getOrDefault("contentJson", Map.of())), clean, Jsoup.parse(clean).text(), values);
    }

    public long write(UUID recordId, long expectedVersion, Prepared value, Instant now) {
        int changed = jdbc.update("UPDATE experiment_records SET title=?,experiment_type=?,experiment_date=?,purpose=?,template_snapshot_json=?,field_values_json=?,content_json=?,content_html_sanitized=?,content_plain_text=?,updated_at=?,version=version+1 WHERE id=? AND version=? AND deleted_at IS NULL",
                value.title(), value.experimentType(), Date.valueOf(value.experimentDate()), value.purpose(), value.templateJson(), value.fieldValuesJson(),
                value.contentJson(), value.contentHtml(), value.contentPlainText(), Timestamp.from(now), recordId.toString(), expectedVersion);
        if (changed != 1) throw new ApiException(HttpStatus.CONFLICT, "OPTIMISTIC_LOCK_CONFLICT", "记录已被其他页面更新");
        return expectedVersion + 1;
    }

    private void identity(Map<String, Object> record, Map<String, Object> snapshot, String rowKey, String snapshotKey) {
        if (!Objects.toString(record.get(rowKey), "").equals(Objects.toString(snapshot.get(snapshotKey), "")))
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "REVISION_IDENTITY_MISMATCH", "修订身份字段不一致: " + snapshotKey);
    }
    private void filterFileFields(Object templateRaw, Map<String, Object> values, Set<UUID> allowed) {
        if (!(templateRaw instanceof Map<?, ?> template) || !(template.get("fields") instanceof List<?> fields)) return;
        for (Object raw : fields) {
            if (!(raw instanceof Map<?, ?> field) || !"FILE".equals(Objects.toString(field.get("fieldType"), ""))) continue;
            String key = Objects.toString(field.get("fieldKey"), ""); Object value = values.get(key);
            Collection<?> items = value instanceof Collection<?> c ? c : value == null ? List.of() : List.of(value);
            List<String> filtered = new ArrayList<>();
            for (Object item : items) try { UUID id = UUID.fromString(item.toString()); if (allowed.contains(id)) filtered.add(id.toString()); } catch (Exception ignored) {}
            values.put(key, filtered);
        }
    }
    @SuppressWarnings("unchecked") private Map<String, Object> map(Object value) { return value instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>(); }
    private String encode(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalArgumentException("Restore snapshot cannot be encoded", e); } }

    public record Prepared(String title, String experimentType, LocalDate experimentDate, String purpose,
                           String templateJson, String fieldValuesJson, String contentJson,
                           String contentHtml, String contentPlainText, Map<String, Object> fieldValues) {}
}
