package com.bionote.restore;

import com.bionote.common.ApiException;
import com.bionote.record.RecordStore;
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
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class WorkingCopySnapshotWriter {
    private static final Safelist CONTENT =
            Safelist.relaxed()
                    .addTags("h2", "h3", "blockquote", "hr", "pre", "code", "s", "strike")
                    .addAttributes("a", "target", "rel");
    private final RecordStore records;
    private final RestoreJsonCodec json;

    public WorkingCopySnapshotWriter(RecordStore records, RestoreJsonCodec json) {
        this.records = records;
        this.json = json;
    }

    public Prepared prepare(
            RecordStore.RecordData record, Map<String, Object> snapshot, Set<UUID> allowedFileIds) {
        identity(record.id(), snapshot, "id");
        identity(record.code(), snapshot, "code");
        identity(record.projectId(), snapshot, "projectId");
        identity(record.creatorId(), snapshot, "creatorId");
        Object template = snapshot.getOrDefault("templateSnapshot", Map.of());
        Map<String, Object> values = map(snapshot.get("fieldValues"));
        filterFileFields(template, values, allowedFileIds);
        String clean = Jsoup.clean(Objects.toString(snapshot.get("contentHtml"), ""), CONTENT);
        return new Prepared(
                Objects.toString(snapshot.get("title"), "").trim(),
                Objects.toString(snapshot.get("experimentType"), "").trim(),
                LocalDate.parse(Objects.toString(snapshot.get("experimentDate"))),
                Objects.toString(snapshot.get("purpose"), "").trim(),
                encode(template),
                encode(values),
                encode(snapshot.getOrDefault("contentJson", Map.of())),
                clean,
                Jsoup.parse(clean).text(),
                values);
    }

    public long write(UUID recordId, long expectedVersion, Prepared value, Instant now) {
        boolean changed =
                records.restoreWorkingCopy(
                        recordId,
                        expectedVersion,
                        new RecordStore.RestoreRecord(
                                value.title(),
                                value.experimentType(),
                                value.experimentDate(),
                                value.purpose(),
                                value.templateJson(),
                                value.fieldValuesJson(),
                                value.contentJson(),
                                value.contentHtml(),
                                value.contentPlainText()),
                        now);
        if (!changed)
            throw new ApiException(HttpStatus.CONFLICT, "OPTIMISTIC_LOCK_CONFLICT", "记录已被其他页面更新");
        return expectedVersion + 1;
    }

    private void identity(Object recordValue, Map<String, Object> snapshot, String snapshotKey) {
        if (!Objects.toString(recordValue, "")
                .equals(Objects.toString(snapshot.get(snapshotKey), "")))
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "REVISION_IDENTITY_MISMATCH",
                    "修订身份字段不一致: " + snapshotKey);
    }

    private void filterFileFields(
            Object templateRaw, Map<String, Object> values, Set<UUID> allowed) {
        if (!(templateRaw instanceof Map<?, ?> template)
                || !(template.get("fields") instanceof List<?> fields)) return;
        for (Object raw : fields) {
            if (!(raw instanceof Map<?, ?> field)
                    || !"FILE".equals(Objects.toString(field.get("fieldType"), ""))) continue;
            String key = Objects.toString(field.get("fieldKey"), "");
            Object value = values.get(key);
            Collection<?> items =
                    value instanceof Collection<?> c
                            ? c
                            : value == null ? List.of() : List.of(value);
            List<String> filtered = new ArrayList<>();
            for (Object item : items)
                try {
                    UUID id = UUID.fromString(item.toString());
                    if (allowed.contains(id)) filtered.add(id.toString());
                } catch (Exception ignored) {
                }
            values.put(key, filtered);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
    }

    private String encode(Object value) {
        return json.encode(value);
    }

    public record Prepared(
            String title,
            String experimentType,
            LocalDate experimentDate,
            String purpose,
            String templateJson,
            String fieldValuesJson,
            String contentJson,
            String contentHtml,
            String contentPlainText,
            Map<String, Object> fieldValues) {}
}
