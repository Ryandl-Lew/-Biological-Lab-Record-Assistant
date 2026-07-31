package com.bionote.revision.infrastructure.persistence;

import com.bionote.revision.RevisionAppender;
import com.bionote.revision.RevisionLookup;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaRevisionPersistenceAdapter implements RevisionAppender, RevisionLookup {
    private final RecordRevisionJpaRepository revisions;

    public JpaRevisionPersistenceAdapter(RecordRevisionJpaRepository revisions) {
        this.revisions = revisions;
    }

    @Override
    public void append(RevisionAppender.RevisionRecord revision) {
        revisions.saveAndFlush(
                new RecordRevisionEntity(
                        revision.id(),
                        revision.recordId(),
                        revision.revisionNo(),
                        revision.snapshotJson(),
                        revision.snapshotSchemaVersion(),
                        revision.contentHash(),
                        revision.submitNote(),
                        revision.submittedBy(),
                        revision.submittedAt(),
                        revision.idempotencyKey()));
    }

    @Override
    public Optional<RevisionLookup.RevisionRecord> findById(UUID revisionId) {
        return revisions.findById(revisionId).map(this::map);
    }

    @Override
    public Optional<RevisionLookup.RevisionRecord> findByRecordAndIdempotencyKey(
            UUID recordId, String key) {
        return revisions.findByRecordIdAndIdempotencyKey(recordId, key).map(this::map);
    }

    @Override
    public List<UUID> findIdsByRecordOrderByRevisionNo(UUID recordId) {
        return revisions.findByRecordIdOrderByRevisionNoAsc(recordId).stream()
                .map(value -> value.id)
                .toList();
    }

    private RevisionLookup.RevisionRecord map(RecordRevisionEntity value) {
        return new RevisionLookup.RevisionRecord(
                value.id,
                value.recordId,
                value.revisionNo,
                value.snapshotJson,
                value.snapshotSchemaVersion,
                value.contentHash,
                value.submitNote,
                value.submittedBy,
                value.submittedAt,
                value.idempotencyKey);
    }
}
