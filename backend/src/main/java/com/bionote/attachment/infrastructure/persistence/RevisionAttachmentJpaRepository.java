package com.bionote.attachment.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

interface RevisionAttachmentJpaRepository extends JpaRepository<RevisionAttachmentEntity,RevisionAttachmentId> {
    List<RevisionAttachmentEntity> findByIdRevisionIdOrderBySortOrderAsc(UUID revisionId);
}
