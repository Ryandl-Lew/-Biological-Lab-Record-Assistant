package com.bionote.project.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProjectRecordReadRepository extends JpaRepository<ProjectRecordReadEntity, UUID> {
    long countByProjectIdAndDeletedAtIsNullAndProvisionalFalse(UUID projectId);

    List<ProjectRecordReadEntity> findByProjectIdAndDeletedAtIsNullAndProvisionalFalseAndStatusNot(
            UUID projectId, String status);

    List<ProjectRecordReadEntity>
            findByProjectIdAndCreatorIdAndDeletedAtIsNullAndProvisionalFalseAndStatusNot(
                    UUID projectId, UUID creatorId, String status);
}
