package com.bionote.agent.infrastructure.persistence;

import com.bionote.agent.api.AgentChatReferenceStore;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaAgentChatReferenceStore implements AgentChatReferenceStore {
    private final AgentChatReferenceJpaRepository references;

    public JpaAgentChatReferenceStore(AgentChatReferenceJpaRepository references) {
        this.references = references;
    }

    @Override
    public void insert(ChatReference value) {
        references.saveAndFlush(
                new AgentChatReferenceEntity(
                        value.id(),
                        value.projectId(),
                        value.uploadedBy(),
                        value.storageKey(),
                        value.originalFilename(),
                        value.contentType(),
                        value.sizeBytes(),
                        value.peekJson(),
                        value.createdAt(),
                        value.expiresAt()));
    }

    @Override
    public Optional<ChatReference> findOwnedActive(
            UUID id, UUID projectId, UUID uploadedBy, Instant now) {
        return references
                .findByIdAndProjectIdAndUploadedByAndExpiresAtAfter(id, projectId, uploadedBy, now)
                .map(this::map);
    }

    @Override
    public Optional<ChatReference> findActive(UUID id, UUID projectId, Instant now) {
        return references.findByIdAndProjectIdAndExpiresAtAfter(id, projectId, now).map(this::map);
    }

    @Override
    public void delete(UUID referenceId) {
        references.deleteById(referenceId);
        references.flush();
    }

    private ChatReference map(AgentChatReferenceEntity value) {
        return new ChatReference(
                value.id,
                value.projectId,
                value.uploadedBy,
                value.storageKey,
                value.originalFilename,
                value.contentType,
                value.sizeBytes,
                value.peekJson,
                value.createdAt,
                value.expiresAt);
    }
}
