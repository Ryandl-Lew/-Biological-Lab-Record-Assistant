package com.bionote.template.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface TemplateFieldJpaRepository extends JpaRepository<TemplateFieldEntity, UUID> {
    List<TemplateFieldEntity> findByTemplateIdOrderBySortOrderAsc(UUID templateId);
    long deleteByTemplateId(UUID templateId);
}
