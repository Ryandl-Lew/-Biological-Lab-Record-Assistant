package com.bionote.project.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface ProjectInvitationJpaRepository extends JpaRepository<ProjectInvitationEntity, UUID> {
    @Query("select i.id from ProjectInvitationEntity i where i.projectId=:projectId and i.status='PENDING'")
    List<UUID> findPendingIds(@Param("projectId") UUID projectId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ProjectInvitationEntity i set i.status='EXPIRED',i.pendingKey=null,i.respondedAt=:now where i.projectId=:projectId and i.status='PENDING'")
    int expireForProject(@Param("projectId") UUID projectId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ProjectInvitationEntity i set i.status='EXPIRED',i.pendingKey=null,i.respondedAt=:now where i.inviteeUserId=:userId and i.status='PENDING' and i.expiresAt<=:now")
    int expireDueForInvitee(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ProjectInvitationEntity i set i.status=:status,i.pendingKey=null,i.respondedAt=:respondedAt where i.id=:id and i.status='PENDING'")
    int changePendingStatus(@Param("id") UUID id, @Param("status") String status, @Param("respondedAt") Instant respondedAt);
}
