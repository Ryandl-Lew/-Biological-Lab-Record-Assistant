package com.bionote.project.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface ProjectRecordReadRepository extends JpaRepository<ProjectRecordReadEntity, UUID> {
    long countByProjectIdAndDeletedAtIsNullAndProvisionalFalse(UUID projectId);
    List<ProjectRecordReadEntity> findByProjectIdAndDeletedAtIsNullAndProvisionalFalseAndStatusNot(UUID projectId, String status);
    List<ProjectRecordReadEntity> findByProjectIdAndCreatorIdAndDeletedAtIsNullAndProvisionalFalseAndStatusNot(UUID projectId, UUID creatorId, String status);
}
