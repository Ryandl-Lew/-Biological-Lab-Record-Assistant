package com.bionote.attachment.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
class RevisionAttachmentId implements Serializable {
    @Column(name="revision_id") @JdbcTypeCode(SqlTypes.CHAR) UUID revisionId;
    @Column(name="attachment_id") @JdbcTypeCode(SqlTypes.CHAR) UUID attachmentId;
    protected RevisionAttachmentId(){}
    RevisionAttachmentId(UUID revisionId,UUID attachmentId){this.revisionId=revisionId;this.attachmentId=attachmentId;}
    @Override public boolean equals(Object value){return value instanceof RevisionAttachmentId other&&Objects.equals(revisionId,other.revisionId)&&Objects.equals(attachmentId,other.attachmentId);}
    @Override public int hashCode(){return Objects.hash(revisionId,attachmentId);}
}
