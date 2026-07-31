package com.bionote.agent.infrastructure.persistence;

import com.bionote.agent.prompt.PromptVersionStore;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaPromptVersionStore implements PromptVersionStore {
    private final PromptVersionJpaRepository prompts;

    public JpaPromptVersionStore(PromptVersionJpaRepository prompts) {
        this.prompts = prompts;
    }

    @Override
    public Optional<StoredPromptVersion> findByNameAndVersion(String name, int version) {
        return prompts.findByPromptNameAndVersionNo(name, version).map(this::map);
    }

    @Override
    public Optional<StoredPromptVersion> findById(UUID id) {
        return prompts.findById(id).map(this::map);
    }

    @Override
    public Optional<StoredPromptVersion> findActive(String name) {
        return prompts.findByPromptNameAndActiveTrue(name).map(this::map);
    }

    @Override
    public void insert(StoredPromptVersion value) {
        prompts.saveAndFlush(
                new PromptVersionEntity(
                        value.id(),
                        value.name(),
                        value.version(),
                        value.templateText(),
                        value.outputSchemaJson(),
                        value.toolPolicyJson(),
                        value.contentHash(),
                        value.active(),
                        value.activeNameKey(),
                        value.createdAt()));
    }

    @Override
    public void deactivateAll(String name) {
        prompts.deactivateAll(name);
    }

    @Override
    public void deactivateOthers(String name, UUID activeId) {
        prompts.deactivateOthers(name, activeId);
    }

    @Override
    public void activate(UUID id, String activeNameKey) {
        prompts.activate(id, activeNameKey);
    }

    private StoredPromptVersion map(PromptVersionEntity value) {
        return new StoredPromptVersion(
                value.id,
                value.promptName,
                value.versionNo,
                value.templateText,
                value.outputSchemaJson,
                value.toolPolicyJson,
                value.contentHash,
                value.active,
                value.activeNameKey,
                value.createdAt);
    }
}
