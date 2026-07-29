package com.bionote.agent.api;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AgentChatReferenceStore {
    void insert(ChatReference reference);
    Optional<ChatReference> findOwnedActive(UUID referenceId, UUID projectId, UUID uploadedBy, Instant now);
    Optional<ChatReference> findActive(UUID referenceId, UUID projectId, Instant now);
    void delete(UUID referenceId);

    record ChatReference(UUID id, UUID projectId, UUID uploadedBy, String storageKey, String originalFilename,
                         String contentType, long sizeBytes, String peekJson, Instant createdAt, Instant expiresAt) {}
}
