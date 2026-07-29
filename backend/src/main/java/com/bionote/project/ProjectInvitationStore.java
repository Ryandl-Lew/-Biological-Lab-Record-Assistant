package com.bionote.project;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectInvitationStore {
    void insert(InvitationRecord invitation);
    Optional<InvitationRecord> findInvitation(UUID invitationId);
    List<UUID> findPendingIds(UUID projectId);
    int expireForProject(UUID projectId, Instant now);
    int expireDueForInvitee(UUID inviteeUserId, Instant now);
    int changePendingStatus(UUID invitationId, String status, Instant respondedAt);

    record InvitationRecord(UUID id, UUID projectId, UUID inviterId, UUID inviteeUserId,
                            String inviteeEmailSnapshot, String status, Instant expiresAt, Instant createdAt,
                            Instant respondedAt, String pendingKey) {}
}
