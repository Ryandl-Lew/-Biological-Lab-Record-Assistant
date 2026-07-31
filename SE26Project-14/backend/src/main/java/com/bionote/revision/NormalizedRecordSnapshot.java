package com.bionote.revision;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record NormalizedRecordSnapshot(
        RevisionDtos.SnapshotSourceRef source,
        UUID recordId,
        Map<String, String> identityFields,
        Map<String, ScalarValue> fixedFields,
        List<NormalizedTemplateField> templateFields,
        List<RichTextBlock> contentBlocks,
        List<NormalizedAttachment> attachments,
        ReviewMetadata reviewMetadata,
        String canonicalHash,
        List<String> warnings) {
    public record ScalarValue(Object displayValue, String normalizedValue) {}

    public record NormalizedTemplateField(
            String fieldKey,
            String label,
            String fieldType,
            boolean required,
            int sortOrder,
            Object displayValue,
            Object normalizedValue) {}

    public record RichTextBlock(String type, String text, int ordinal) {}

    public record NormalizedAttachment(
            UUID id,
            String filename,
            String mediaType,
            long sizeBytes,
            UUID uploaderId,
            String uploaderName,
            Instant createdAt) {}

    public record ReviewMetadata(
            String submitNote,
            UUID reviewerId,
            String reviewerName,
            String status,
            String decisionComment,
            Instant decidedAt) {}
}
