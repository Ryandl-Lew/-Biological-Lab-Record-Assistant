package com.bionote.agent.infrastructure.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AgentArtifactJpaRepository extends JpaRepository<AgentArtifactEntity, UUID> {
    Optional<AgentArtifactEntity> findByRunId(UUID runId);
    List<AgentArtifactEntity> findByProjectIdOrderByCreatedAtDescIdDesc(UUID projectId, Pageable pageable);
    long countByProjectId(UUID projectId);
    List<AgentArtifactEntity> findByRecordIdOrderByCreatedAtDescIdDesc(UUID recordId, Pageable pageable);
    long countByRecordId(UUID recordId);
}
