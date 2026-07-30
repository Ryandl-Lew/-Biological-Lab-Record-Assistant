package com.bionote.record;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for mutable record working copies. */
public interface RecordStore {
    void insert(NewRecord record);

    Optional<RecordData> findActive(UUID recordId);

    Optional<RecordData> findActiveForUpdate(UUID recordId);

    PageSlice searchVisible(
            UUID userId,
            UUID projectId,
            UUID creatorId,
            String status,
            String escapedKeyword,
            int page,
            int size);

    boolean updateWorkingCopy(UUID recordId, long expectedVersion, UpdateRecord update);

    void replaceFieldValuesWithoutVersion(UUID recordId, String fieldValuesJson);

    boolean markSubmitted(
            UUID recordId, long expectedVersion, int revisionNo, UUID reviewId, Instant now);

    boolean markReviewApproved(UUID recordId, UUID reviewId, UUID revisionId, Instant now);

    boolean markReviewChangesRequested(UUID recordId, UUID reviewId, Instant now);

    boolean restoreWorkingCopy(
            UUID recordId, long expectedVersion, RestoreRecord value, Instant now);

    boolean softDelete(UUID recordId, long expectedVersion, Instant now);

    boolean deleteProvisional(UUID recordId, UUID creatorId);

    record PageSlice(List<UUID> ids, long total) {}

    record NewRecord(
            UUID id,
            String code,
            UUID projectId,
            UUID creatorId,
            String title,
            String experimentType,
            LocalDate experimentDate,
            String purpose,
            boolean provisional,
            String templateSnapshotJson,
            String fieldValuesJson,
            String contentJson,
            String contentHtmlSanitized,
            String contentPlainText,
            Instant now) {}

    record UpdateRecord(
            String title,
            String experimentType,
            LocalDate experimentDate,
            String purpose,
            String fieldValuesJson,
            String contentJson,
            String contentHtmlSanitized,
            String contentPlainText,
            boolean provisional,
            Instant now) {}

    record RestoreRecord(
            String title,
            String experimentType,
            LocalDate experimentDate,
            String purpose,
            String templateSnapshotJson,
            String fieldValuesJson,
            String contentJson,
            String contentHtmlSanitized,
            String contentPlainText) {}

    record RecordData(
            UUID id,
            String code,
            UUID projectId,
            UUID creatorId,
            String title,
            String experimentType,
            LocalDate experimentDate,
            String purpose,
            String status,
            boolean provisional,
            String templateSnapshotJson,
            String fieldValuesJson,
            String contentJson,
            String contentHtmlSanitized,
            String contentPlainText,
            int currentRevisionNo,
            UUID currentReviewId,
            UUID finalRevisionId,
            long version,
            Instant createdAt,
            Instant updatedAt,
            Instant deletedAt) {}
}
