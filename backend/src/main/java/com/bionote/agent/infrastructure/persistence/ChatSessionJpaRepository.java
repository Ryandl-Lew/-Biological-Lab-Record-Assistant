package com.bionote.agent.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionJpaRepository extends JpaRepository<ChatSessionEntity, UUID> {
    List<ChatSessionEntity> findByUserIdAndProjectIdOrderByUpdatedAtDesc(
            UUID userId, UUID projectId);

    List<ChatSessionEntity> findByUserIdAndRecordIdOrderByUpdatedAtDesc(UUID userId, UUID recordId);
}
