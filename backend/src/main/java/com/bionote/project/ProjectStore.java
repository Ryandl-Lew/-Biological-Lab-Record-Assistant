package com.bionote.project;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectStore {
    void insert(ProjectRecord project);

    Optional<ProjectRecord> findById(UUID projectId);

    PageSlice searchForMember(
            UUID userId, String status, String escapedKeyword, int page, int size);

    int archiveActive(UUID projectId, Instant now);

    long recordCount(UUID projectId);

    record ProjectRecord(
            UUID id,
            String name,
            String description,
            String detailedDescription,
            String status,
            UUID ownerId,
            Instant createdAt,
            Instant updatedAt,
            Instant archivedAt,
            long version) {}

    record ProjectAccess(ProjectRecord project, String role) {}

    record PageSlice(List<ProjectAccess> items, long total) {}
}
