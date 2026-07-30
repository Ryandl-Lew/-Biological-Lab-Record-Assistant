package com.bionote.template.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TemplateFieldJpaRepository extends JpaRepository<TemplateFieldEntity, UUID> {
    List<TemplateFieldEntity> findByTemplateIdOrderBySortOrderAsc(UUID templateId);

    long deleteByTemplateId(UUID templateId);
}
