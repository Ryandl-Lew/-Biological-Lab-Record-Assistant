package com.bionote.attachment.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AttachmentJpaRepository extends JpaRepository<AttachmentEntity,UUID> {
    List<AttachmentEntity> findByRecordIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(UUID recordId);
    List<AttachmentEntity> findByRecordIdOrderByCreatedAtAscIdAsc(UUID recordId);
    boolean existsByIdAndRecordIdAndDeletedAtIsNull(UUID id,UUID recordId);
    @Query("select a from AttachmentEntity a where a.id=:id and (a.deletedAt is null or exists(select ra.id from RevisionAttachmentEntity ra where ra.id.attachmentId=a.id))")
    Optional<AttachmentEntity> findAccessible(@Param("id") UUID id);
    @Query("select a.storageKey from AttachmentEntity a where a.recordId=:recordId") List<String> storageKeys(@Param("recordId")UUID recordId);
    @Modifying(clearAutomatically=true,flushAutomatically=true) @Query("delete from AttachmentEntity a where a.recordId=:recordId")
    int deleteForRecord(@Param("recordId")UUID recordId);
    @Modifying(clearAutomatically=true,flushAutomatically=true) @Query("update AttachmentEntity a set a.deletedAt=null where a.id=:id")
    int reactivate(@Param("id")UUID id);
}
