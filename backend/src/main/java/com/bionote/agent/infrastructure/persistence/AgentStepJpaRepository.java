package com.bionote.agent.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface AgentStepJpaRepository extends JpaRepository<AgentStepEntity, UUID> {
    List<AgentStepEntity> findByRunIdOrderByStepNoAsc(UUID runId);
}
