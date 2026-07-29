package com.bionote.attachment.infrastructure.persistence;

import com.bionote.attachment.AttachmentStore;
import com.bionote.revision.RevisionAttachmentAppender;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaAttachmentStore implements AttachmentStore, RevisionAttachmentAppender {
    private final AttachmentJpaRepository attachments;private final RevisionAttachmentJpaRepository revisionAttachments;
    @PersistenceContext private EntityManager entityManager;
    public JpaAttachmentStore(AttachmentJpaRepository attachments,RevisionAttachmentJpaRepository revisionAttachments){this.attachments=attachments;this.revisionAttachments=revisionAttachments;}
    @Override public void insert(AttachmentRecord a){attachments.saveAndFlush(new AttachmentEntity(a.id(),a.recordId(),a.uploaderId(),a.originalFilename(),a.storageKey(),a.mediaType(),a.sizeBytes(),a.previewable(),a.createdAt()));}
    @Override public Optional<AttachmentRecord> findById(UUID id,boolean includeDeleted){return (includeDeleted?attachments.findById(id):attachments.findAccessible(id)).map(this::map);}
    @Override public List<AttachmentRecord> findActiveByRecord(UUID recordId){return attachments.findByRecordIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(recordId).stream().map(this::map).toList();}
    @Override public List<AttachmentRecord> findAllByRecord(UUID recordId){return attachments.findByRecordIdOrderByCreatedAtAscIdAsc(recordId).stream().map(this::map).toList();}
    @Override public List<AttachmentRecord> findByRevision(UUID revisionId){return revisionAttachments.findByIdRevisionIdOrderBySortOrderAsc(revisionId).stream().map(ra->attachments.findById(ra.id.attachmentId).map(this::map).orElseThrow()).toList();}
    @Override public Optional<UUID> findRevisionProjectId(UUID revisionId){List<?> values=entityManager.createNativeQuery("SELECT r.project_id FROM record_revisions rv JOIN experiment_records r ON r.id=rv.record_id WHERE rv.id=:id").setParameter("id",revisionId.toString()).getResultList();return values.isEmpty()?Optional.empty():Optional.of(UUID.fromString(values.get(0).toString()));}
    @Override public boolean softDelete(UUID id,Instant deletedAt){var entity=attachments.findById(id).orElse(null);if(entity==null||entity.deletedAt!=null)return false;entity.deletedAt=deletedAt;attachments.saveAndFlush(entity);return true;}
    @Override public boolean reactivate(UUID id){return attachments.reactivate(id)==1;}
    @Override public boolean existsActive(UUID id,UUID recordId){return attachments.existsByIdAndRecordIdAndDeletedAtIsNull(id,recordId);}
    @Override public List<String> storageKeysByRecord(UUID recordId){return attachments.storageKeys(recordId);}
    @Override public void deleteByRecord(UUID recordId){attachments.deleteForRecord(recordId);}
    @Override public void append(UUID revisionId,List<UUID> attachmentIds){
        for(int index=0;index<attachmentIds.size();index++)
            revisionAttachments.save(new RevisionAttachmentEntity(new RevisionAttachmentId(revisionId,attachmentIds.get(index)),index));
        revisionAttachments.flush();
    }
    private AttachmentRecord map(AttachmentEntity a){return new AttachmentRecord(a.id,a.recordId,a.uploaderId,a.originalFilename,a.storageKey,a.mediaType,a.sizeBytes,a.previewable,a.createdAt,a.deletedAt);}
}
