package com.bionote.project;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectMemberStore {
    void insert(UUID projectId, UUID userId, String role, Instant joinedAt, Instant lastActiveAt);
    Optional<String> findRole(UUID projectId, UUID userId);
    boolean exists(UUID projectId, UUID userId);
    long count(UUID projectId);
    List<UUID> memberIds(UUID projectId);
    List<MemberRecord> list(UUID projectId);
    Optional<MemberRecord> findMember(UUID projectId, UUID userId);
    void touch(UUID projectId, UUID userId, Instant now);
    void updateRole(UUID projectId, UUID userId, String role);
    void remove(UUID projectId, UUID userId);

    record MemberRecord(UUID userId, String displayName, String email, String avatarStorageKey, String role,
                        Instant joinedAt, Instant lastActiveAt) {}
}
