package com.bionote.agent.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AgentStepJpaRepository extends JpaRepository<AgentStepEntity, UUID> {
    List<AgentStepEntity> findByRunIdOrderByStepNoAsc(UUID runId);
}
