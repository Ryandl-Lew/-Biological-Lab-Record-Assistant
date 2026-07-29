package com.bionote.attachment.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="attachments")
class AttachmentEntity {
    @Id @JdbcTypeCode(SqlTypes.CHAR) UUID id;
    @Column(name="record_id",nullable=false) @JdbcTypeCode(SqlTypes.CHAR) UUID recordId;
    @Column(name="uploader_id",nullable=false) @JdbcTypeCode(SqlTypes.CHAR) UUID uploaderId;
    @Column(name="original_filename",nullable=false,length=255) String originalFilename;
    @Column(name="storage_key",nullable=false,length=36) @JdbcTypeCode(SqlTypes.CHAR) String storageKey;
    @Column(name="media_type",nullable=false,length=120) String mediaType;
    @Column(name="size_bytes",nullable=false) long sizeBytes;
    @Column(nullable=false) boolean previewable;
    @Column(name="created_at",nullable=false) Instant createdAt;
    @Column(name="deleted_at") Instant deletedAt;
    protected AttachmentEntity(){}
    AttachmentEntity(UUID id,UUID recordId,UUID uploaderId,String originalFilename,String storageKey,String mediaType,
                     long sizeBytes,boolean previewable,Instant createdAt){this.id=id;this.recordId=recordId;this.uploaderId=uploaderId;
        this.originalFilename=originalFilename;this.storageKey=storageKey;this.mediaType=mediaType;this.sizeBytes=sizeBytes;
        this.previewable=previewable;this.createdAt=createdAt;}
}
