package com.bionote.project.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface ProjectMemberJpaRepository extends JpaRepository<ProjectMemberEntity, ProjectMemberId> {
    interface MemberProjection {
        String getUserId(); String getDisplayName(); String getEmail(); String getAvatarStorageKey();
        String getRole(); Instant getJoinedAt(); Instant getLastActiveAt();
    }

    long countByIdProjectId(UUID projectId);

    @Query("select pm.id.userId from ProjectMemberEntity pm where pm.id.projectId=:projectId")
    List<UUID> findMemberIds(@Param("projectId") UUID projectId);

    @Query(value = """
            SELECT pm.user_id AS userId,u.display_name AS displayName,u.email_normalized AS email,
                   u.avatar_storage_key AS avatarStorageKey,pm.role,pm.joined_at AS joinedAt,
                   pm.last_active_at AS lastActiveAt
              FROM project_members pm JOIN users u ON u.id=pm.user_id
             WHERE pm.project_id=:projectId
             ORDER BY CASE pm.role WHEN 'OWNER' THEN 0 WHEN 'REVIEWER' THEN 1 ELSE 2 END,u.display_name
            """, nativeQuery = true)
    List<MemberProjection> listMembers(@Param("projectId") String projectId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ProjectMemberEntity pm set pm.lastActiveAt=:now where pm.id.projectId=:projectId and pm.id.userId=:userId")
    int touch(@Param("projectId") UUID projectId, @Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ProjectMemberEntity pm set pm.role=:role where pm.id.projectId=:projectId and pm.id.userId=:userId")
    int updateRole(@Param("projectId") UUID projectId, @Param("userId") UUID userId, @Param("role") String role);
}
