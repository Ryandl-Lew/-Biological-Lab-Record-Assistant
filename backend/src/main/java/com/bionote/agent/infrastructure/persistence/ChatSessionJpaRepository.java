package com.bionote.agent.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ChatSessionJpaRepository extends JpaRepository<ChatSessionEntity, UUID> {
    List<ChatSessionEntity> findByUserIdAndProjectIdOrderByUpdatedAtDesc(UUID userId, UUID projectId);
    List<ChatSessionEntity> findByUserIdAndRecordIdOrderByUpdatedAtDesc(UUID userId, UUID recordId);
}
