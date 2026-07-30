package com.bionote.restore;

import com.bionote.record.RecordDtos;
import com.bionote.revision.RevisionDtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RestoreDtos {
    private RestoreDtos() {}

    public record PreviewRequest(
            @NotNull UUID sourceRevisionId,
            @NotNull Long expectedRecordVersion,
            @NotNull Boolean restoreAttachments) {}

    public record ExecuteRequest(
            @NotNull UUID sourceRevisionId,
            @NotNull Long expectedRecordVersion,
            @NotNull Boolean restoreAttachments,
            @NotBlank String previewToken) {}

    public record SourceRevision(UUID id, int revisionNo) {}

    public record AttachmentPlan(
            List<UUID> activate,
            List<UUID> softDelete,
            List<UUID> keep,
            Map<String, List<UUID>> droppedFileFieldReferences,
            List<UUID> missingPhysicalFiles) {}

    public record Preview(
            UUID recordId,
            SourceRevision sourceRevision,
            long expectedRecordVersion,
            String previewToken,
            Instant expiresAt,
            RevisionDtos.DiffResult diff,
            AttachmentPlan attachmentPlan,
            List<String> warnings,
            Map<String, Boolean> capabilities) {}

    public record OperationSummary(
            UUID id,
            UUID recordId,
            UUID sourceRevisionId,
            int sourceRevisionNo,
            UUID actorId,
            String actorName,
            long beforeRecordVersion,
            long afterRecordVersion,
            String beforeContentHash,
            String afterContentHash,
            RevisionDtos.DiffSummary diffSummary,
            Instant restoredAt) {}

    public record Result(RecordDtos.View record, OperationSummary operation) {}
}
