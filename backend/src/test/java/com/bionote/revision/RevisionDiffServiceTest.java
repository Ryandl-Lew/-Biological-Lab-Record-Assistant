package com.bionote.revision;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
class RevisionDiffServiceTest {
    private final UUID actor = UUID.randomUUID(), record = UUID.randomUUID(), project = UUID.randomUUID(), creator = UUID.randomUUID();

    @Test void comparesFixedTemplateRichTextAttachmentsAndReviewSeparately() {
        UUID r1 = UUID.randomUUID(), r2 = UUID.randomUUID(), attachment1 = UUID.randomUUID(), attachment2 = UUID.randomUUID();
        NormalizedRecordSnapshot before = snapshot(r1, 1, "旧标题", "旧目的", "25", "旧说明", "第一段", attachment1, "CHANGES_REQUESTED");
        NormalizedRecordSnapshot after = snapshot(r2, 2, "新标题", "新目的", "25.0", "新说明", "修改段落", attachment2, "APPROVED");
        SnapshotSourceProvider resolver = provider(Map.of(r1, before, r2, after), null);
        RevisionDtos.DiffResult result = new RevisionDiffService(resolver, new TextDiffEngine(), 100000, 500, 500)
                .compare(actor, record, r1, r2, null, true);
        assertThat(result.sections()).extracting(RevisionDtos.DiffSection::key)
                .contains("fixed:title", "fixed:purpose", "field:note", "content:block:0", "attachments", "review");
        assertThat(result.sections().stream().filter(s -> s.key().equals("field:amount")).findFirst().orElseThrow().status()).isEqualTo("UNCHANGED");
        assertThat(result.summary().attachmentAdded()).isEqualTo(1); assertThat(result.summary().attachmentRemoved()).isEqualTo(1);
        assertThat(result.sections().stream().filter(s -> s.key().equals("review")).findFirst().orElseThrow().kind()).isEqualTo("REVIEW_METADATA");
        assertThat(result.summary().modified()).isGreaterThanOrEqualTo(4);
    }

    @Test void comparesRevisionWithWorkingCopyAndTruncatesLargeText() {
        UUID r1 = UUID.randomUUID();
        NormalizedRecordSnapshot before = snapshot(r1, 1, "标题", "目的", "25", "说明", "短文本", null, "PENDING");
        NormalizedRecordSnapshot working = new NormalizedRecordSnapshot(new RevisionDtos.SnapshotSourceRef("WORKING_COPY", null, null, 7L, "working-hash"),
                record, before.identityFields(), before.fixedFields(), before.templateFields(),
                List.of(new NormalizedRecordSnapshot.RichTextBlock("paragraph", "x".repeat(100), 0)), List.of(), null, "working-hash", List.of());
        SnapshotSourceProvider resolver = provider(Map.of(r1, before), working);
        RevisionDtos.DiffResult result = new RevisionDiffService(resolver, new TextDiffEngine(), 20, 10, 50)
                .compare(actor, record, r1, null, "WORKING_COPY", false);
        assertThat(result.to().type()).isEqualTo("WORKING_COPY"); assertThat(result.to().recordVersion()).isEqualTo(7L);
        assertThat(result.truncated()).isTrue(); assertThat(result.warnings()).isNotEmpty();
    }

    private NormalizedRecordSnapshot snapshot(UUID revisionId, int revisionNo, String title, String purpose,
            String amount, String note, String paragraph, UUID attachmentId, String reviewStatus) {
        Map<String, NormalizedRecordSnapshot.ScalarValue> fixed = new LinkedHashMap<>();
        fixed.put("title", new NormalizedRecordSnapshot.ScalarValue(title, title));
        fixed.put("experimentType", new NormalizedRecordSnapshot.ScalarValue("PCR", "PCR"));
        fixed.put("experimentDate", new NormalizedRecordSnapshot.ScalarValue("2026-07-26", "2026-07-26"));
        fixed.put("purpose", new NormalizedRecordSnapshot.ScalarValue(purpose, purpose));
        List<NormalizedRecordSnapshot.NormalizedTemplateField> fields = List.of(
                new NormalizedRecordSnapshot.NormalizedTemplateField("amount", "用量", "NUMBER", true, 0, amount, new java.math.BigDecimal(amount).stripTrailingZeros().toPlainString()),
                new NormalizedRecordSnapshot.NormalizedTemplateField("note", "说明", "MULTI_LINE_TEXT", false, 1, note, note));
        List<NormalizedRecordSnapshot.NormalizedAttachment> attachments = attachmentId == null ? List.of() : List.of(
                new NormalizedRecordSnapshot.NormalizedAttachment(attachmentId, attachmentId + ".png", "image/png", 10, creator, "创建者", Instant.now()));
        return new NormalizedRecordSnapshot(new RevisionDtos.SnapshotSourceRef("REVISION", revisionId, revisionNo, null, "hash-" + revisionNo), record,
                Map.of("id", record.toString(), "code", "EXP-1", "projectId", project.toString(), "creatorId", creator.toString()),
                fixed, fields, List.of(new NormalizedRecordSnapshot.RichTextBlock("paragraph", paragraph, 0)), attachments,
                new NormalizedRecordSnapshot.ReviewMetadata("提交说明", UUID.randomUUID(), "审核者", reviewStatus, "意见", Instant.now()), "canonical-" + revisionNo, List.of());
    }

    private SnapshotSourceProvider provider(Map<UUID, NormalizedRecordSnapshot> revisions, NormalizedRecordSnapshot workingCopy) {
        return new SnapshotSourceProvider() {
            @Override public NormalizedRecordSnapshot revision(UUID actorId, UUID recordId, UUID revisionId) { return revisions.get(revisionId); }
            @Override public NormalizedRecordSnapshot workingCopy(UUID actorId, UUID recordId) { return workingCopy; }
        };
    }
}
