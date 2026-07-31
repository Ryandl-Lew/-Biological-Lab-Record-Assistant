package com.bionote.attachment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttachmentStore {
    void insert(AttachmentRecord attachment);

    Optional<AttachmentRecord> findById(UUID attachmentId, boolean includeDeleted);

    List<AttachmentRecord> findActiveByRecord(UUID recordId);

    List<AttachmentRecord> findAllByRecord(UUID recordId);

    List<AttachmentRecord> findByRevision(UUID revisionId);

    Optional<UUID> findRevisionProjectId(UUID revisionId);

    boolean softDelete(UUID attachmentId, Instant deletedAt);

    boolean reactivate(UUID attachmentId);

    boolean existsActive(UUID attachmentId, UUID recordId);

    List<String> storageKeysByRecord(UUID recordId);

    void deleteByRecord(UUID recordId);

    record AttachmentRecord(
            UUID id,
            UUID recordId,
            UUID uploaderId,
            String originalFilename,
            String storageKey,
            String mediaType,
            long sizeBytes,
            boolean previewable,
            Instant createdAt,
            Instant deletedAt) {}
}
