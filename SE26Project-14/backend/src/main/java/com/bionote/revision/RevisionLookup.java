package com.bionote.revision;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Narrow lookup port used by submission/review workflows. */
public interface RevisionLookup {
    Optional<RevisionRecord> findById(UUID revisionId);

    Optional<RevisionRecord> findByRecordAndIdempotencyKey(UUID recordId, String idempotencyKey);

    List<UUID> findIdsByRecordOrderByRevisionNo(UUID recordId);

    record RevisionRecord(
            UUID id,
            UUID recordId,
            int revisionNo,
            String snapshotJson,
            int snapshotSchemaVersion,
            String contentHash,
            String submitNote,
            UUID submittedBy,
            Instant submittedAt,
            String idempotencyKey) {}
}
