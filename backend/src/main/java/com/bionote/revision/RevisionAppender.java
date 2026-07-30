package com.bionote.revision;

import java.time.Instant;
import java.util.UUID;

/** Append-only persistence port for immutable revision snapshots. */
public interface RevisionAppender {
    void append(RevisionRecord revision);

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
