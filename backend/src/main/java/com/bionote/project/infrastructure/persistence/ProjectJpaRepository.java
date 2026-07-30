package com.bionote.project.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ProjectJpaRepository extends JpaRepository<ProjectEntity, UUID> {
    interface AccessProjection {
        String getId();

        String getName();

        String getDescription();

        String getDetailedDescription();

        String getStatus();

        String getOwnerId();

        Instant getCreatedAt();

        Instant getUpdatedAt();

        Instant getArchivedAt();

        long getVersion();

        String getRole();
    }

    @Query(
            value =
                    """
            SELECT p.id,p.name,p.description,p.detailed_description AS detailedDescription,p.status,
                   p.owner_id AS ownerId,p.created_at AS createdAt,p.updated_at AS updatedAt,
                   p.archived_at AS archivedAt,p.version,pm.role
              FROM projects p JOIN project_members pm ON pm.project_id=p.id
             WHERE pm.user_id=:userId
               AND (:status IS NULL OR p.status=:status)
               AND (LOWER(p.name) LIKE CONCAT('%',:keyword,'%') ESCAPE '!'
                    OR LOWER(COALESCE(p.description,'')) LIKE CONCAT('%',:keyword,'%') ESCAPE '!'
                    OR LOWER(COALESCE(p.detailed_description,'')) LIKE CONCAT('%',:keyword,'%') ESCAPE '!')
             ORDER BY p.updated_at DESC,p.id DESC
            """,
            countQuery =
                    """
            SELECT COUNT(*) FROM projects p JOIN project_members pm ON pm.project_id=p.id
             WHERE pm.user_id=:userId
               AND (:status IS NULL OR p.status=:status)
               AND (LOWER(p.name) LIKE CONCAT('%',:keyword,'%') ESCAPE '!'
                    OR LOWER(COALESCE(p.description,'')) LIKE CONCAT('%',:keyword,'%') ESCAPE '!'
                    OR LOWER(COALESCE(p.detailed_description,'')) LIKE CONCAT('%',:keyword,'%') ESCAPE '!')
            """,
            nativeQuery = true)
    Page<AccessProjection> searchForMember(
            @Param("userId") String userId,
            @Param("status") String status,
            @Param("keyword") String escapedKeyword,
            Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "update ProjectEntity p set p.status='ARCHIVED',p.archivedAt=:now,p.updatedAt=:now,p.version=p.version+1 where p.id=:id and p.status='ACTIVE'")
    int archiveActive(@Param("id") UUID id, @Param("now") Instant now);
}
