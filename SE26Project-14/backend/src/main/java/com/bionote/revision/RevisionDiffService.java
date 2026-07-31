package com.bionote.revision;

import com.bionote.common.ApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RevisionDiffService implements RevisionDiffUseCase {
    private static final List<String> FIXED_KEYS =
            List.of("title", "experimentType", "experimentDate", "purpose");
    private static final Map<String, String> FIXED_LABELS =
            Map.of(
                    "title",
                    "实验名称",
                    "experimentType",
                    "实验类型",
                    "experimentDate",
                    "实验日期",
                    "purpose",
                    "实验目的");
    private final SnapshotSourceProvider resolver;
    private final TextDiffEngine textDiff;
    private final int maxTextChars;
    private final int maxHunks;
    private final int maxSections;

    public RevisionDiffService(
            SnapshotSourceProvider resolver,
            TextDiffEngine textDiff,
            @Value("${revision.diff.max-text-chars:30000}") int maxTextChars,
            @Value("${revision.diff.max-hunks:200}") int maxHunks,
            @Value("${revision.diff.max-sections:200}") int maxSections) {
        this.resolver = resolver;
        this.textDiff = textDiff;
        this.maxTextChars = maxTextChars;
        this.maxHunks = maxHunks;
        this.maxSections = maxSections;
    }

    public RevisionDtos.DiffResult compare(
            UUID actorId,
            UUID recordId,
            UUID fromRevisionId,
            UUID toRevisionId,
            String to,
            boolean includeUnchanged) {
        if (fromRevisionId == null
                || (toRevisionId == null && !"WORKING_COPY".equals(to))
                || (toRevisionId != null && to != null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DIFF_SOURCE", "请选择有效的两个比较来源");
        }
        NormalizedRecordSnapshot from = resolver.revision(actorId, recordId, fromRevisionId);
        NormalizedRecordSnapshot target =
                toRevisionId != null
                        ? resolver.revision(actorId, recordId, toRevisionId)
                        : resolver.workingCopy(actorId, recordId);
        return compare(from, target, recordId, includeUnchanged);
    }

    public RevisionDtos.DiffResult compareWorkingCopyToRevision(
            UUID actorId, UUID recordId, UUID sourceRevisionId, boolean includeUnchanged) {
        NormalizedRecordSnapshot from = resolver.workingCopy(actorId, recordId);
        NormalizedRecordSnapshot target = resolver.revision(actorId, recordId, sourceRevisionId);
        return compare(from, target, recordId, includeUnchanged);
    }

    private RevisionDtos.DiffResult compare(
            NormalizedRecordSnapshot from,
            NormalizedRecordSnapshot target,
            UUID recordId,
            boolean includeUnchanged) {
        validateIdentity(from, target);
        DiffAccumulator output = new DiffAccumulator(includeUnchanged);
        diffFixed(from, target, output);
        diffTemplate(from, target, output);
        diffRichText(from, target, output);
        diffAttachments(from, target, output);
        diffReview(from, target, output);
        output.warnings.addAll(from.warnings());
        output.warnings.addAll(target.warnings());
        if (output.sections.size() > maxSections) {
            output.sections = new ArrayList<>(output.sections.subList(0, maxSections));
            output.truncated = true;
            output.warnings.add("差异区段过多，仅展示前 " + maxSections + " 项");
        }
        return new RevisionDtos.DiffResult(
                recordId,
                from.source(),
                target.source(),
                new RevisionDtos.DiffSummary(
                        output.added,
                        output.removed,
                        output.modified,
                        output.unchanged,
                        output.attachmentAdded,
                        output.attachmentRemoved),
                List.copyOf(output.sections),
                output.truncated,
                List.copyOf(new LinkedHashSet<>(output.warnings)),
                Instant.now());
    }

    private void validateIdentity(NormalizedRecordSnapshot from, NormalizedRecordSnapshot to) {
        if (!from.recordId().equals(to.recordId()))
            throw new ApiException(HttpStatus.NOT_FOUND, "REVISION_NOT_FOUND", "修订不属于当前记录");
        for (String key : List.of("id", "code", "projectId", "creatorId"))
            if (!Objects.equals(from.identityFields().get(key), to.identityFields().get(key)))
                throw new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "REVISION_IDENTITY_MISMATCH",
                        "修订身份字段不一致: " + key);
    }

    private void diffFixed(
            NormalizedRecordSnapshot from, NormalizedRecordSnapshot to, DiffAccumulator output) {
        for (String key : FIXED_KEYS) {
            var before = from.fixedFields().get(key);
            var after = to.fixedFields().get(key);
            String status =
                    status(
                            before == null ? null : before.normalizedValue(),
                            after == null ? null : after.normalizedValue());
            List<RevisionDtos.TextOperation> hunks = List.of();
            if ("MODIFIED".equals(status) && "purpose".equals(key))
                hunks =
                        diffText(
                                Objects.toString(before.displayValue(), ""),
                                Objects.toString(after.displayValue(), ""),
                                output);
            output.add(
                    new RevisionDtos.DiffSection(
                            "fixed:" + key,
                            FIXED_LABELS.get(key),
                            "SCALAR",
                            "purpose".equals(key) ? "MULTI_LINE_TEXT" : "TEXT",
                            status,
                            display(before),
                            display(after),
                            hunks,
                            Map.of()),
                    true);
        }
    }

    private void diffTemplate(
            NormalizedRecordSnapshot from, NormalizedRecordSnapshot to, DiffAccumulator output) {
        Map<String, NormalizedRecordSnapshot.NormalizedTemplateField>
                before = byFieldKey(from.templateFields()),
                after = byFieldKey(to.templateFields());
        Set<String> keys = new LinkedHashSet<>(before.keySet());
        keys.addAll(after.keySet());
        for (String key : keys) {
            var left = before.get(key);
            var right = after.get(key);
            String status =
                    left == null
                            ? "ADDED"
                            : right == null
                                    ? "REMOVED"
                                    : status(left.normalizedValue(), right.normalizedValue());
            if (left != null
                    && right != null
                    && (!left.label().equals(right.label())
                            || !left.fieldType().equals(right.fieldType()))) status = "MODIFIED";
            String type = right != null ? right.fieldType() : left.fieldType();
            List<RevisionDtos.TextOperation> hunks = List.of();
            if ("MODIFIED".equals(status) && "MULTI_LINE_TEXT".equals(type))
                hunks =
                        diffText(
                                Objects.toString(left.displayValue(), ""),
                                Objects.toString(right.displayValue(), ""),
                                output);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("fieldKey", key);
            details.put("requiredBefore", left != null && left.required());
            details.put("requiredAfter", right != null && right.required());
            details.put("labelBefore", left == null ? null : left.label());
            details.put("labelAfter", right == null ? null : right.label());
            output.add(
                    new RevisionDtos.DiffSection(
                            "field:" + key,
                            right != null ? right.label() : left.label(),
                            "TEMPLATE_FIELD",
                            type,
                            status,
                            left == null ? null : left.displayValue(),
                            right == null ? null : right.displayValue(),
                            hunks,
                            details),
                    true);
        }
    }

    private void diffRichText(
            NormalizedRecordSnapshot from, NormalizedRecordSnapshot to, DiffAccumulator output) {
        List<NormalizedRecordSnapshot.RichTextBlock> left = from.contentBlocks(),
                right = to.contentBlocks();
        int i = 0, j = 0, displayOrdinal = 0;
        while (i < left.size() || j < right.size()) {
            NormalizedRecordSnapshot.RichTextBlock a = i < left.size() ? left.get(i) : null,
                    b = j < right.size() ? right.get(j) : null;
            String status;
            if (a == null) {
                status = "ADDED";
                j++;
            } else if (b == null) {
                status = "REMOVED";
                i++;
            } else if (a.type().equals(b.type()) && a.text().equals(b.text())) {
                status = "UNCHANGED";
                i++;
                j++;
            } else if (j + 1 < right.size()
                    && a.type().equals(right.get(j + 1).type())
                    && a.text().equals(right.get(j + 1).text())) {
                status = "ADDED";
                b = right.get(j++);
            } else if (i + 1 < left.size()
                    && left.get(i + 1).type().equals(b.type())
                    && left.get(i + 1).text().equals(b.text())) {
                status = "REMOVED";
                a = left.get(i++);
            } else {
                status = "MODIFIED";
                i++;
                j++;
            }
            List<RevisionDtos.TextOperation> hunks =
                    "MODIFIED".equals(status)
                            ? diffText(a == null ? "" : a.text(), b == null ? "" : b.text(), output)
                            : List.of();
            output.add(
                    new RevisionDtos.DiffSection(
                            "content:block:" + displayOrdinal,
                            "正文块 " + (displayOrdinal + 1),
                            "RICH_TEXT",
                            b != null ? b.type() : a.type(),
                            status,
                            a == null ? null : limited(a.text(), output),
                            b == null ? null : limited(b.text(), output),
                            hunks,
                            Map.of("ordinal", displayOrdinal)),
                    true);
            displayOrdinal++;
        }
    }

    private void diffAttachments(
            NormalizedRecordSnapshot from, NormalizedRecordSnapshot to, DiffAccumulator output) {
        Map<UUID, NormalizedRecordSnapshot.NormalizedAttachment>
                left = byAttachment(from.attachments()),
                right = byAttachment(to.attachments());
        List<Map<String, Object>> added = new ArrayList<>(),
                removed = new ArrayList<>(),
                unchanged = new ArrayList<>(),
                metadataModified = new ArrayList<>();
        for (var entry : right.entrySet())
            if (!left.containsKey(entry.getKey())) added.add(attachment(entry.getValue()));
        for (var entry : left.entrySet())
            if (!right.containsKey(entry.getKey())) removed.add(attachment(entry.getValue()));
        for (UUID id : left.keySet())
            if (right.containsKey(id)) {
                Map<String, Object> value = attachment(right.get(id));
                if (!sameAttachment(left.get(id), right.get(id))) metadataModified.add(value);
                else unchanged.add(value);
            }
        output.attachmentAdded = added.size();
        output.attachmentRemoved = removed.size();
        String status =
                !added.isEmpty() && removed.isEmpty()
                        ? "ADDED"
                        : added.isEmpty() && !removed.isEmpty()
                                ? "REMOVED"
                                : (!added.isEmpty()
                                                || !removed.isEmpty()
                                                || !metadataModified.isEmpty())
                                        ? "MODIFIED"
                                        : "UNCHANGED";
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("addedAttachments", added);
        details.put("removedAttachments", removed);
        details.put("metadataModified", metadataModified);
        if (output.includeUnchanged) details.put("unchangedAttachments", unchanged);
        output.add(
                new RevisionDtos.DiffSection(
                        "attachments",
                        "附件",
                        "ATTACHMENT_SET",
                        "ATTACHMENT",
                        status,
                        left.values().stream().map(this::attachment).toList(),
                        right.values().stream().map(this::attachment).toList(),
                        List.of(),
                        details),
                false);
    }

    private void diffReview(
            NormalizedRecordSnapshot from, NormalizedRecordSnapshot to, DiffAccumulator output) {
        if (from.reviewMetadata() == null && to.reviewMetadata() == null) return;
        Map<String, Object> before = review(from.reviewMetadata()),
                after = review(to.reviewMetadata());
        String status = status(before, after);
        output.add(
                new RevisionDtos.DiffSection(
                        "review",
                        "审核信息",
                        "REVIEW_METADATA",
                        "REVIEW",
                        status,
                        before,
                        after,
                        List.of(),
                        Map.of()),
                false);
    }

    private List<RevisionDtos.TextOperation> diffText(
            String before, String after, DiffAccumulator output) {
        String left = limited(before, output), right = limited(after, output);
        TextDiffEngine.Result result = textDiff.diff(left, right, maxHunks);
        if (result.truncated()) {
            output.truncated = true;
            output.warnings.add("文本差异过大，hunk 已裁剪");
        }
        return result.operations();
    }

    private String limited(String value, DiffAccumulator output) {
        if (value == null || value.length() <= maxTextChars) return value;
        output.truncated = true;
        output.warnings.add("正文超过 " + maxTextChars + " 字符，仅展示部分");
        return value.substring(0, maxTextChars);
    }

    private String status(Object before, Object after) {
        if (Objects.equals(empty(before), empty(after))) return "UNCHANGED";
        if (isEmpty(before)) return "ADDED";
        if (isEmpty(after)) return "REMOVED";
        return "MODIFIED";
    }

    private Object empty(Object value) {
        return isEmpty(value) ? null : value;
    }

    private boolean isEmpty(Object value) {
        return value == null
                || value instanceof String text && text.isEmpty()
                || value instanceof java.util.Collection<?> collection && collection.isEmpty();
    }

    private Object display(NormalizedRecordSnapshot.ScalarValue value) {
        return value == null ? null : value.displayValue();
    }

    private Map<String, NormalizedRecordSnapshot.NormalizedTemplateField> byFieldKey(
            List<NormalizedRecordSnapshot.NormalizedTemplateField> fields) {
        Map<String, NormalizedRecordSnapshot.NormalizedTemplateField> result =
                new LinkedHashMap<>();
        fields.forEach(field -> result.put(field.fieldKey(), field));
        return result;
    }

    private Map<UUID, NormalizedRecordSnapshot.NormalizedAttachment> byAttachment(
            List<NormalizedRecordSnapshot.NormalizedAttachment> attachments) {
        Map<UUID, NormalizedRecordSnapshot.NormalizedAttachment> result = new LinkedHashMap<>();
        attachments.forEach(value -> result.put(value.id(), value));
        return result;
    }

    private Map<String, Object> attachment(NormalizedRecordSnapshot.NormalizedAttachment value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", value.id());
        result.put("filename", value.filename());
        result.put("mediaType", value.mediaType());
        result.put("sizeBytes", value.sizeBytes());
        result.put("uploaderId", value.uploaderId());
        result.put("uploaderName", value.uploaderName());
        result.put("createdAt", value.createdAt());
        return result;
    }

    private boolean sameAttachment(
            NormalizedRecordSnapshot.NormalizedAttachment a,
            NormalizedRecordSnapshot.NormalizedAttachment b) {
        return Objects.equals(a.filename(), b.filename())
                && Objects.equals(a.mediaType(), b.mediaType())
                && a.sizeBytes() == b.sizeBytes();
    }

    private Map<String, Object> review(NormalizedRecordSnapshot.ReviewMetadata value) {
        if (value == null) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submitNote", value.submitNote());
        result.put("reviewerId", value.reviewerId());
        result.put("reviewerName", value.reviewerName());
        result.put("status", value.status());
        result.put("decisionComment", value.decisionComment());
        result.put("decidedAt", value.decidedAt());
        return result;
    }

    private static final class DiffAccumulator {
        private final boolean includeUnchanged;
        private List<RevisionDtos.DiffSection> sections = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private int added;
        private int removed;
        private int modified;
        private int unchanged;
        private int attachmentAdded;
        private int attachmentRemoved;
        private boolean truncated;

        private DiffAccumulator(boolean includeUnchanged) {
            this.includeUnchanged = includeUnchanged;
        }

        private void add(RevisionDtos.DiffSection section, boolean countContent) {
            if (countContent)
                switch (section.status()) {
                    case "ADDED" -> added++;
                    case "REMOVED" -> removed++;
                    case "MODIFIED" -> modified++;
                    default -> unchanged++;
                }
            if (!"UNCHANGED".equals(section.status()) || includeUnchanged) sections.add(section);
        }
    }
}
