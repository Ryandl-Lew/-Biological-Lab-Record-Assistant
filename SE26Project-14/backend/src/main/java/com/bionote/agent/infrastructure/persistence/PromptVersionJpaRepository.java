package com.bionote.agent.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PromptVersionJpaRepository extends JpaRepository<PromptVersionEntity, UUID> {
    Optional<PromptVersionEntity> findByPromptNameAndVersionNo(String promptName, int versionNo);

    Optional<PromptVersionEntity> findByPromptNameAndActiveTrue(String promptName);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "update PromptVersionEntity p set p.active=false,p.activeNameKey=null where p.promptName=:name and p.active=true")
    int deactivateAll(@Param("name") String name);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "update PromptVersionEntity p set p.active=false,p.activeNameKey=null where p.promptName=:name and p.id<>:id and p.active=true")
    int deactivateOthers(@Param("name") String name, @Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PromptVersionEntity p set p.active=true,p.activeNameKey=:name where p.id=:id")
    int activate(@Param("id") UUID id, @Param("name") String name);
}
