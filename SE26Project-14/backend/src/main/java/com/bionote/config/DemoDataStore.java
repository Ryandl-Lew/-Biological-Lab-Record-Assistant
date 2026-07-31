package com.bionote.config;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary used only by the development data seeders. */
public interface DemoDataStore {
    Optional<UUID> findProjectIdByName(String name);

    Optional<UUID> findUserIdByEmail(String normalizedEmail);

    Optional<String> findEmailByUserId(UUID userId);

    Optional<UUID> findChangesRequestedRevisionOneRecordByTitlePrefix(String titlePrefix);

    Optional<UUID> findRecordCreatorId(UUID recordId);

    boolean activeAttachmentExists(UUID recordId, String originalFilename);

    void alignRecordTimes(
            UUID recordId, Instant createdAt, Instant updatedAt, Instant attachmentCreatedAt);

    void spreadProjectTimeline(UUID projectId, Instant start, long stepHours);
}
