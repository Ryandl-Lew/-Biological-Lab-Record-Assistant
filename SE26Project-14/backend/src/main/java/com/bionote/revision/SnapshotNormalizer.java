package com.bionote.revision;

import com.bionote.common.ApiException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class SnapshotNormalizer {
    private static final List<String> FIXED_KEYS =
            List.of("title", "experimentType", "experimentDate", "purpose");
    private static final Set<String> KNOWN_FIELD_TYPES =
            Set.of("SINGLE_LINE_TEXT", "MULTI_LINE_TEXT", "NUMBER", "DATE", "SELECT", "FILE");
    private final ObjectMapper canonicalJson;

    public SnapshotNormalizer(ObjectMapper objectMapper) {
        canonicalJson = objectMapper.copy();
        canonicalJson.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        canonicalJson.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
    }

    public NormalizedRecordSnapshot normalize(RevisionStore.SourceRecord source) {
        if (source.schemaVersion() != 1)
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "REVISION_SCHEMA_UNSUPPORTED", "不支持的修订快照版本");
        Map<String, Object> snapshot = source.snapshot();
        UUID recordId = requiredUuid(snapshot.get("id"), "record id");
        Map<String, String> identity = new LinkedHashMap<>();
        for (String key : List.of("id", "code", "projectId", "creatorId"))
            identity.put(key, Objects.toString(snapshot.get(key), ""));
        Map<String, NormalizedRecordSnapshot.ScalarValue> fixed = new LinkedHashMap<>();
        for (String key : FIXED_KEYS) {
            Object display = snapshot.get(key);
            fixed.put(
                    key,
                    new NormalizedRecordSnapshot.ScalarValue(
                            display, normalizeFixed(key, display)));
        }
        List<String> warnings = new ArrayList<>();
        List<NormalizedRecordSnapshot.NormalizedTemplateField> templateFields =
                normalizeTemplateFields(snapshot, warnings);
        List<NormalizedRecordSnapshot.RichTextBlock> blocks =
                normalizeBlocks(
                        snapshot.get("contentJson"),
                        Objects.toString(snapshot.get("contentPlainText"), ""));
        List<NormalizedRecordSnapshot.NormalizedAttachment> attachments =
                source.attachments().stream()
                        .map(
                                value ->
                                        new NormalizedRecordSnapshot.NormalizedAttachment(
                                                value.id(),
                                                value.filename(),
                                                value.mediaType(),
                                                value.sizeBytes(),
                                                value.uploaderId(),
                                                value.uploaderName(),
                                                value.createdAt()))
                        .toList();
        NormalizedRecordSnapshot.ReviewMetadata review =
                source.review() == null
                        ? null
                        : new NormalizedRecordSnapshot.ReviewMetadata(
                                source.submitNote(),
                                source.review().reviewerId(),
                                source.review().reviewerName(),
                                source.review().status(),
                                source.review().decisionComment(),
                                source.review().decidedAt());
        String canonicalHash = canonicalHash(fixed, templateFields, blocks, attachments);
        RevisionDtos.SnapshotSourceRef ref = source.source();
        if ("WORKING_COPY".equals(ref.type()))
            ref =
                    new RevisionDtos.SnapshotSourceRef(
                            ref.type(), null, null, ref.recordVersion(), canonicalHash);
        return new NormalizedRecordSnapshot(
                ref,
                recordId,
                Map.copyOf(identity),
                Map.copyOf(fixed),
                List.copyOf(templateFields),
                List.copyOf(blocks),
                List.copyOf(attachments),
                review,
                canonicalHash,
                List.copyOf(warnings));
    }

    private List<NormalizedRecordSnapshot.NormalizedTemplateField> normalizeTemplateFields(
            Map<String, Object> snapshot, List<String> warnings) {
        Map<String, Object> fieldValues = asMap(snapshot.get("fieldValues"));
        Map<String, Object> template = asMap(snapshot.get("templateSnapshot"));
        Object rawFields = template.get("fields");
        if (!(rawFields instanceof List<?> list)) return List.of();
        List<NormalizedRecordSnapshot.NormalizedTemplateField> result = new ArrayList<>();
        int fallbackOrder = 0;
        for (Object raw : list) {
            if (!(raw instanceof Map<?, ?> input)) continue;
            String key = Objects.toString(input.get("fieldKey"), "").trim();
            if (key.isBlank()) continue;
            String label = Objects.toString(input.get("label"), key);
            String type = Objects.toString(input.get("fieldType"), "UNKNOWN");
            boolean required = Boolean.TRUE.equals(input.get("required"));
            int sortOrder = number(input.get("sortOrder"), fallbackOrder++);
            Object displayValue = fieldValues.get(key);
            Object normalizedValue = normalizeTemplateValue(type, displayValue);
            if (!KNOWN_FIELD_TYPES.contains(type)) warnings.add("未知模板字段类型 " + type + " 已按字符串比较");
            result.add(
                    new NormalizedRecordSnapshot.NormalizedTemplateField(
                            key, label, type, required, sortOrder, displayValue, normalizedValue));
        }
        result.sort(
                java.util.Comparator.comparingInt(
                                NormalizedRecordSnapshot.NormalizedTemplateField::sortOrder)
                        .thenComparing(NormalizedRecordSnapshot.NormalizedTemplateField::fieldKey));
        return result;
    }

    private Object normalizeTemplateValue(String type, Object value) {
        if (value == null) return null;
        return switch (type) {
            case "NUMBER" -> normalizeNumber(value);
            case "DATE" -> normalizeDate(value);
            case "FILE" -> normalizeFileIds(value);
            case "SINGLE_LINE_TEXT", "MULTI_LINE_TEXT", "SELECT" -> normalizeText(value.toString());
            default -> normalizeText(value.toString());
        };
    }

    private List<String> normalizeFileIds(Object value) {
        Collection<?> values =
                value instanceof Collection<?> collection ? collection : List.of(value);
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (Object item : values)
            if (item != null && !item.toString().isBlank()) ids.add(item.toString());
        return List.copyOf(ids);
    }

    private String normalizeFixed(String key, Object value) {
        if (value == null) return "";
        if ("experimentDate".equals(key)) return normalizeDate(value);
        return normalizeText(value.toString());
    }

    private String normalizeNumber(Object value) {
        try {
            return new BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
        } catch (Exception ignored) {
            return normalizeText(value.toString());
        }
    }

    private String normalizeDate(Object value) {
        try {
            return LocalDate.parse(value.toString()).toString();
        } catch (Exception ignored) {
            return normalizeText(value.toString());
        }
    }

    private String normalizeText(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private List<NormalizedRecordSnapshot.RichTextBlock> normalizeBlocks(
            Object contentJson, String plainText) {
        List<BlockDraft> drafts = new ArrayList<>();
        if (contentJson instanceof Map<?, ?> root) appendNodes(root.get("content"), drafts, null);
        if (drafts.isEmpty() && !plainText.isBlank()) {
            String[] lines = normalizeText(plainText).split("\\n\\s*\\n|\\n");
            for (String line : lines)
                if (!line.isBlank()) drafts.add(new BlockDraft("paragraph", normalizeText(line)));
        }
        List<NormalizedRecordSnapshot.RichTextBlock> result = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++)
            result.add(
                    new NormalizedRecordSnapshot.RichTextBlock(
                            drafts.get(i).type(), drafts.get(i).text(), i));
        return result;
    }

    private void appendNodes(Object rawNodes, List<BlockDraft> result, String listType) {
        if (!(rawNodes instanceof List<?> nodes)) return;
        for (Object raw : nodes) {
            if (!(raw instanceof Map<?, ?> node)) continue;
            String type = Objects.toString(node.get("type"), "");
            switch (type) {
                case "paragraph" ->
                        result.add(
                                new BlockDraft(
                                        listType == null ? "paragraph" : listType,
                                        collectText(node)));
                case "heading" ->
                        result.add(
                                new BlockDraft(
                                        "heading:" + attribute(node, "level", "1"),
                                        collectText(node)));
                case "blockquote" -> result.add(new BlockDraft("blockquote", collectText(node)));
                case "codeBlock" -> result.add(new BlockDraft("code_block", collectText(node)));
                case "horizontalRule" -> result.add(new BlockDraft("horizontal_rule", ""));
                case "bulletList" -> appendNodes(node.get("content"), result, "bullet_list_item");
                case "orderedList" -> appendNodes(node.get("content"), result, "ordered_list_item");
                case "listItem" -> {
                    String text = collectText(node);
                    if (!text.isBlank())
                        result.add(new BlockDraft(listType == null ? "list_item" : listType, text));
                }
                default -> {
                    Object children = node.get("content");
                    if (children != null) appendNodes(children, result, listType);
                }
            }
        }
    }

    private String collectText(Map<?, ?> node) {
        StringBuilder text = new StringBuilder();
        collectTextRecursive(node, text);
        return normalizeText(text.toString());
    }

    private void collectTextRecursive(Object raw, StringBuilder text) {
        if (raw instanceof Map<?, ?> node) {
            if ("text".equals(node.get("type")) && node.get("text") != null)
                text.append(node.get("text"));
            Object content = node.get("content");
            if (content != null) collectTextRecursive(content, text);
        } else if (raw instanceof List<?> list)
            for (Object item : list) collectTextRecursive(item, text);
    }

    private String attribute(Map<?, ?> node, String key, String fallback) {
        Object attrs = node.get("attrs");
        return attrs instanceof Map<?, ?> map ? Objects.toString(map.get(key), fallback) : fallback;
    }

    private String canonicalHash(
            Map<String, NormalizedRecordSnapshot.ScalarValue> fixed,
            List<NormalizedRecordSnapshot.NormalizedTemplateField> templateFields,
            List<NormalizedRecordSnapshot.RichTextBlock> blocks,
            List<NormalizedRecordSnapshot.NormalizedAttachment> attachments) {
        try {
            Map<String, Object> canonical = new TreeMap<>();
            Map<String, Object> fixedValues = new TreeMap<>();
            fixed.forEach((key, value) -> fixedValues.put(key, value.normalizedValue()));
            canonical.put("fixedFields", fixedValues);
            canonical.put(
                    "templateFields",
                    templateFields.stream()
                            .map(
                                    field ->
                                            Map.of(
                                                    "fieldKey",
                                                    field.fieldKey(),
                                                    "label",
                                                    field.label(),
                                                    "fieldType",
                                                    field.fieldType(),
                                                    "required",
                                                    field.required(),
                                                    "sortOrder",
                                                    field.sortOrder(),
                                                    "value",
                                                    field.normalizedValue() == null
                                                            ? ""
                                                            : field.normalizedValue()))
                            .toList());
            canonical.put(
                    "contentBlocks",
                    blocks.stream()
                            .map(
                                    block ->
                                            Map.of(
                                                    "type",
                                                    block.type(),
                                                    "text",
                                                    block.text(),
                                                    "ordinal",
                                                    block.ordinal()))
                            .toList());
            canonical.put(
                    "attachmentIds",
                    attachments.stream().map(value -> value.id().toString()).toList());
            byte[] bytes = canonicalJson.writeValueAsBytes(canonical);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot calculate canonical revision hash", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : Map.of();
    }

    private UUID requiredUuid(Object value, String label) {
        try {
            return UUID.fromString(value.toString());
        } catch (Exception e) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "REVISION_SNAPSHOT_INVALID",
                    "修订快照缺少 " + label);
        }
    }

    private int number(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private record BlockDraft(String type, String text) {}
}
