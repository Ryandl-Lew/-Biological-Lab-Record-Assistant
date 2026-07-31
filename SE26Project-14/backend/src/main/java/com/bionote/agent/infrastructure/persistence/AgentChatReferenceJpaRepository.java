package com.bionote.agent.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AgentChatReferenceJpaRepository extends JpaRepository<AgentChatReferenceEntity, UUID> {
    Optional<AgentChatReferenceEntity> findByIdAndProjectIdAndUploadedByAndExpiresAtAfter(
            UUID id, UUID projectId, UUID uploadedBy, Instant now);

    Optional<AgentChatReferenceEntity> findByIdAndProjectIdAndExpiresAtAfter(
            UUID id, UUID projectId, Instant now);
}
