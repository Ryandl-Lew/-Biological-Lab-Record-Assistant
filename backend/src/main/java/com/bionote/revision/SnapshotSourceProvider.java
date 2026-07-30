package com.bionote.revision;

import java.util.UUID;

public interface SnapshotSourceProvider {
    NormalizedRecordSnapshot revision(UUID actorId, UUID recordId, UUID revisionId);

    NormalizedRecordSnapshot workingCopy(UUID actorId, UUID recordId);
}
