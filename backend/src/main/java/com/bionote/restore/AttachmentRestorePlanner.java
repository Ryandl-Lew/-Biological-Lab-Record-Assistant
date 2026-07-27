package com.bionote.restore;

import com.bionote.attachment.AttachmentStorageService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
public class AttachmentRestorePlanner {
    private final JdbcTemplate jdbc;
    private final AttachmentStorageService storage;

    public AttachmentRestorePlanner(JdbcTemplate jdbc, AttachmentStorageService storage) {
        this.jdbc = jdbc; this.storage = storage;
    }

    public Plan plan(UUID recordId, UUID sourceRevisionId, Map<String, Object> sourceSnapshot,
                     boolean restoreAttachments) {
        List<Map<String, Object>> all = jdbc.queryForList("SELECT id,storage_key,deleted_at FROM attachments WHERE record_id=? ORDER BY created_at,id", recordId.toString());
        Set<UUID> active = new LinkedHashSet<>(); Map<UUID, String> storageKeys = new LinkedHashMap<>();
        for (Map<String, Object> row : all) {
            UUID id = UUID.fromString(row.get("id").toString()); storageKeys.put(id, row.get("storage_key").toString());
            if (row.get("deleted_at") == null) active.add(id);
        }
        Set<UUID> source = new LinkedHashSet<>(jdbc.query("SELECT a.id FROM revision_attachments ra JOIN attachments a ON a.id=ra.attachment_id WHERE ra.revision_id=? AND a.record_id=? ORDER BY ra.sort_order",
                (rs, n) -> UUID.fromString(rs.getString(1)), sourceRevisionId.toString(), recordId.toString()));
        List<UUID> missing = source.stream().filter(id -> !storageKeys.containsKey(id) || !storage.exists(storageKeys.get(id))).toList();
        Set<UUID> activate = new LinkedHashSet<>(), softDelete = new LinkedHashSet<>(), keep = new LinkedHashSet<>();
        if (restoreAttachments) {
            source.forEach(id -> { if (active.contains(id)) keep.add(id); else activate.add(id); });
            active.stream().filter(id -> !source.contains(id)).forEach(softDelete::add);
        } else keep.addAll(active);
        Map<String, List<UUID>> dropped = restoreAttachments ? Map.of() : droppedFileReferences(sourceSnapshot, active);
        RestoreDtos.AttachmentPlan dto = new RestoreDtos.AttachmentPlan(List.copyOf(activate), List.copyOf(softDelete),
                List.copyOf(keep), dropped, missing);
        return new Plan(dto, Set.copyOf(active), Set.copyOf(source));
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<UUID>> droppedFileReferences(Map<String, Object> snapshot, Set<UUID> active) {
        Object templateRaw = snapshot.get("templateSnapshot"), valuesRaw = snapshot.get("fieldValues");
        if (!(templateRaw instanceof Map<?, ?> template) || !(valuesRaw instanceof Map<?, ?> values) || !(template.get("fields") instanceof List<?> fields)) return Map.of();
        Map<String, List<UUID>> dropped = new LinkedHashMap<>();
        for (Object raw : fields) {
            if (!(raw instanceof Map<?, ?> field) || !"FILE".equals(Objects.toString(field.get("fieldType"), ""))) continue;
            String key = Objects.toString(field.get("fieldKey"), ""); Object value = values.get(key);
            Collection<?> items = value instanceof Collection<?> collection ? collection : value == null ? List.of() : List.of(value);
            List<UUID> invalid = new ArrayList<>();
            for (Object item : items) try { UUID id = UUID.fromString(item.toString()); if (!active.contains(id)) invalid.add(id); } catch (Exception ignored) {}
            if (!invalid.isEmpty()) dropped.put(key, List.copyOf(invalid));
        }
        return Map.copyOf(dropped);
    }

    public record Plan(RestoreDtos.AttachmentPlan dto, Set<UUID> currentActive, Set<UUID> source) {}
}
