package com.bionote.restore;

import com.bionote.revision.RevisionDtos;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface RestoreOperationStore {
    Stored find(UUID recordId, String idempotencyKey);

    void append(OperationRecord operation);

    PageSlice list(UUID recordId, int page, int size);

    record OperationRecord(
            UUID id,
            UUID recordId,
            UUID sourceRevisionId,
            UUID actorId,
            long beforeVersion,
            long afterVersion,
            String beforeHash,
            String afterHash,
            RevisionDtos.DiffSummary summary,
            String idempotencyKey,
            String payloadHash,
            Instant restoredAt) {}

    record Stored(RestoreDtos.OperationSummary summary, String payloadHash) {}

    record PageSlice(List<RestoreDtos.OperationSummary> items, long total) {}
}
