package com.bionote.template.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface RecordTemplateJpaRepository
        extends JpaRepository<RecordTemplateEntity, UUID>,
                JpaSpecificationExecutor<RecordTemplateEntity> {
    boolean existsByActiveNameKey(String activeNameKey);
}
