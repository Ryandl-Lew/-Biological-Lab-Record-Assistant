package com.bionote.agent.prompt;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PromptVersionStore {
    Optional<StoredPromptVersion> findByNameAndVersion(String name, int version);
    Optional<StoredPromptVersion> findById(UUID id);
    Optional<StoredPromptVersion> findActive(String name);
    void insert(StoredPromptVersion version);
    void deactivateAll(String name);
    void deactivateOthers(String name, UUID activeId);
    void activate(UUID id, String activeNameKey);

    record StoredPromptVersion(UUID id, String name, int version, String templateText,
                               String outputSchemaJson, String toolPolicyJson, String contentHash,
                               boolean active, String activeNameKey, Instant createdAt) {}
}
