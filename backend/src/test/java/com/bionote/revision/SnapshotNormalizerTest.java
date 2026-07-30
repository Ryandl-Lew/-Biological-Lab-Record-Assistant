package com.bionote.revision;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SnapshotNormalizerTest {
    private final SnapshotNormalizer normalizer = new SnapshotNormalizer(new ObjectMapper());
    private final UUID recordId = UUID.randomUUID(),
            projectId = UUID.randomUUID(),
            creatorId = UUID.randomUUID();

    @Test
    void keyOrderAndEquivalentNumbersProduceSameCanonicalHash() {
        Map<String, Object> first =
                snapshot(
                        25,
                        Map.of(
                                "type",
                                "doc",
                                "content",
                                List.of(
                                        Map.of(
                                                "type",
                                                "paragraph",
                                                "content",
                                                List.of(Map.of("type", "text", "text", "结果稳定"))))));
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("contentPlainText", "fallback");
        second.put("contentJson", first.get("contentJson"));
        second.put("fieldValues", Map.of("amount", 25.0));
        second.put("templateSnapshot", first.get("templateSnapshot"));
        second.put("purpose", "目的");
        second.put("experimentDate", "2026-07-26");
        second.put("experimentType", "PCR");
        second.put("title", "记录");
        second.put("creatorId", creatorId.toString());
        second.put("projectId", projectId.toString());
        second.put("code", "EXP-1");
        second.put("id", recordId.toString());
        assertThat(normalize(first).canonicalHash()).isEqualTo(normalize(second).canonicalHash());
        assertThat(normalize(first).templateFields().get(0).normalizedValue()).isEqualTo("25");
    }

    @Test
    void tiptapBlocksAreStableAndHtmlIsNotAnInput() {
        Map<String, Object> content =
                Map.of(
                        "type",
                        "doc",
                        "content",
                        List.of(
                                Map.of(
                                        "type",
                                        "heading",
                                        "attrs",
                                        Map.of("level", 2),
                                        "content",
                                        List.of(Map.of("type", "text", "text", "结果"))),
                                Map.of(
                                        "type",
                                        "paragraph",
                                        "content",
                                        List.of(Map.of("type", "text", "text", "Ct 值 22"))),
                                Map.of("type", "horizontalRule")));
        NormalizedRecordSnapshot value = normalize(snapshot(null, content));
        assertThat(value.contentBlocks())
                .extracting(NormalizedRecordSnapshot.RichTextBlock::type)
                .containsExactly("heading:2", "paragraph", "horizontal_rule");
        assertThat(value.contentBlocks().get(1).text()).isEqualTo("Ct 值 22");
    }

    @Test
    void invalidOrEmptyContentJsonFallsBackToPlainText() {
        Map<String, Object> snapshot = snapshot(null, Map.of());
        snapshot.put("contentPlainText", "第一段\n\n第二段");
        assertThat(normalize(snapshot).contentBlocks())
                .extracting(NormalizedRecordSnapshot.RichTextBlock::text)
                .containsExactly("第一段", "第二段");
    }

    @Test
    void unknownFieldTypeIsRetainedWithWarning() {
        Map<String, Object> snapshot = snapshot(null, Map.of());
        snapshot.put(
                "templateSnapshot",
                Map.of(
                        "fields",
                        List.of(
                                Map.of(
                                        "fieldKey",
                                        "legacy",
                                        "label",
                                        "旧字段",
                                        "fieldType",
                                        "LEGACY",
                                        "sortOrder",
                                        0))));
        snapshot.put("fieldValues", Map.of("legacy", "保留值"));
        NormalizedRecordSnapshot value = normalize(snapshot);
        assertThat(value.templateFields().get(0).normalizedValue()).isEqualTo("保留值");
        assertThat(value.warnings()).anyMatch(message -> message.contains("LEGACY"));
    }

    private NormalizedRecordSnapshot normalize(Map<String, Object> snapshot) {
        RevisionStore.SourceRecord source =
                new RevisionStore.SourceRecord(
                        new RevisionDtos.SnapshotSourceRef(
                                "REVISION", UUID.randomUUID(), 1, null, "source-hash"),
                        1,
                        snapshot,
                        List.of(),
                        null,
                        null,
                        null);
        return normalizer.normalize(source);
    }

    private Map<String, Object> snapshot(Object amount, Object contentJson) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", recordId.toString());
        value.put("code", "EXP-1");
        value.put("projectId", projectId.toString());
        value.put("creatorId", creatorId.toString());
        value.put("title", "记录");
        value.put("experimentType", "PCR");
        value.put("experimentDate", "2026-07-26");
        value.put("purpose", "目的");
        value.put(
                "templateSnapshot",
                Map.of(
                        "name",
                        "模板",
                        "version",
                        1,
                        "fields",
                        List.of(
                                Map.of(
                                        "fieldKey",
                                        "amount",
                                        "label",
                                        "用量",
                                        "fieldType",
                                        "NUMBER",
                                        "required",
                                        true,
                                        "sortOrder",
                                        0))));
        value.put("fieldValues", amount == null ? Map.of() : Map.of("amount", amount));
        value.put("contentJson", contentJson);
        value.put("contentPlainText", "");
        return value;
    }
}
