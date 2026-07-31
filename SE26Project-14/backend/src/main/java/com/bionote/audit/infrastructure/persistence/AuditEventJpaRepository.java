package com.bionote.audit.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AuditEventJpaRepository extends JpaRepository<AuditEventEntity, UUID> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    """
            INSERT INTO audit_events(id,actor_id,project_id,record_id,event_type,target_type,target_id,metadata_json,created_at)
            VALUES(:id,:actorId,:projectId,:recordId,:eventType,:targetType,:targetId,:metadata,:createdAt)
            ON DUPLICATE KEY UPDATE id=id
            """,
            nativeQuery = true)
    int appendOnce(
            @Param("id") String id,
            @Param("actorId") String actorId,
            @Param("projectId") String projectId,
            @Param("recordId") String recordId,
            @Param("eventType") String eventType,
            @Param("targetType") String targetType,
            @Param("targetId") String targetId,
            @Param("metadata") String metadata,
            @Param("createdAt") Instant createdAt);
}
