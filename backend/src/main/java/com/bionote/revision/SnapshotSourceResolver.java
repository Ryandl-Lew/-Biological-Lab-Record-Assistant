package com.bionote.revision;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SnapshotSourceResolver implements SnapshotSourceProvider {
    private final RevisionStore repository;
    private final SnapshotNormalizer normalizer;

    public SnapshotSourceResolver(RevisionStore repository, SnapshotNormalizer normalizer) {
        this.repository = repository;
        this.normalizer = normalizer;
    }

    @Override
    public NormalizedRecordSnapshot revision(UUID actorId, UUID recordId, UUID revisionId) {
        return normalizer.normalize(repository.revisionSource(actorId, recordId, revisionId));
    }

    @Override
    public NormalizedRecordSnapshot workingCopy(UUID actorId, UUID recordId) {
        return normalizer.normalize(repository.workingCopySource(actorId, recordId));
    }
}
