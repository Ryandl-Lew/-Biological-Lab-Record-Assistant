package com.bionote.revision;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RevisionDtos {
    private RevisionDtos() {}

    public record ReviewSummary(
            UUID id,
            UUID reviewerId,
            String reviewerName,
            String status,
            String decisionComment,
            Instant assignedAt,
            Instant decidedAt,
            boolean canDecide) {}

    public record AttachmentView(
            UUID id,
            String filename,
            String mediaType,
            long sizeBytes,
            UUID uploaderId,
            String uploaderName,
            Instant createdAt) {}

    public record RevisionSummary(
            UUID id,
            UUID recordId,
            int revisionNo,
            String label,
            UUID submittedBy,
            String submitterName,
            Instant submittedAt,
            String submitNote,
            String contentHash,
            ReviewSummary review,
            int attachmentCount,
            boolean current,
            boolean finalRevision) {}

    public record SnapshotView(
            UUID recordId,
            String code,
            UUID projectId,
            UUID creatorId,
            String title,
            String experimentType,
            String experimentDate,
            String purpose,
            Object templateSnapshot,
            Object fieldValues,
            Object contentJson,
            String contentPlainText) {}

    public record RevisionDetail(
            UUID id,
            UUID recordId,
            int revisionNo,
            String label,
            int snapshotSchemaVersion,
            SnapshotView snapshot,
            String contentHash,
            UUID submittedBy,
            String submitterName,
            Instant submittedAt,
            String submitNote,
            ReviewSummary review,
            List<AttachmentView> attachments,
            boolean current,
            boolean finalRevision) {}

    public record SnapshotSourceRef(
            String type,
            UUID revisionId,
            Integer revisionNo,
            Long recordVersion,
            String contentHash) {}

    public record TextOperation(String operation, String text) {}

    public record DiffSection(
            String key,
            String label,
            String kind,
            String valueType,
            String status,
            Object before,
            Object after,
            List<TextOperation> textHunks,
            Map<String, Object> details) {}

    public record DiffSummary(
            int added,
            int removed,
            int modified,
            int unchanged,
            int attachmentAdded,
            int attachmentRemoved) {}

    public record DiffResult(
            UUID recordId,
            SnapshotSourceRef from,
            SnapshotSourceRef to,
            DiffSummary summary,
            List<DiffSection> sections,
            boolean truncated,
            List<String> warnings,
            Instant generatedAt) {}
}
