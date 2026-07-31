package com.bionote.attachment.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RevisionAttachmentJpaRepository
        extends JpaRepository<RevisionAttachmentEntity, RevisionAttachmentId> {
    List<RevisionAttachmentEntity> findByIdRevisionIdOrderBySortOrderAsc(UUID revisionId);
}
