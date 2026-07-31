package com.bionote.record;

import java.util.Optional;
import java.util.UUID;

/** Read-only port for choosing the immutable revision displayed by a record detail response. */
public interface RecordRevisionDisplayReader {
    Optional<DisplayedRevision> findCompleted(UUID finalRevisionId);

    Optional<DisplayedRevision> findInReview(UUID currentReviewId);

    record DisplayedRevision(UUID id, int revisionNo, String snapshotJson) {}
}
