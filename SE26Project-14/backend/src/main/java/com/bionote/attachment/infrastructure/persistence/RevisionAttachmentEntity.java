package com.bionote.attachment.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "revision_attachments")
class RevisionAttachmentEntity {
    @EmbeddedId RevisionAttachmentId id;

    @Column(name = "sort_order", nullable = false)
    int sortOrder;

    protected RevisionAttachmentEntity() {}

    RevisionAttachmentEntity(RevisionAttachmentId id, int sortOrder) {
        this.id = id;
        this.sortOrder = sortOrder;
    }
}
