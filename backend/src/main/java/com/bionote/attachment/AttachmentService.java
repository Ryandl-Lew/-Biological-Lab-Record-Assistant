package com.bionote.attachment;

import com.bionote.collaboration.CollaborationEvents;
import com.bionote.common.ApiException;
import com.bionote.project.ProjectMemberStore;
import com.bionote.project.ProjectStore;
import com.bionote.record.RecordStore;
import com.bionote.user.User;
import com.bionote.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AttachmentService implements AttachmentUseCase {
    private final AttachmentStore attachments;
    private final RecordStore records;
    private final ProjectStore projects;
    private final ProjectMemberStore members;
    private final UserRepository users;
    private final AttachmentStorage storage;
    private final CollaborationEvents events;

    public AttachmentService(AttachmentStore attachments,RecordStore records,ProjectStore projects,
                             ProjectMemberStore members,UserRepository users,AttachmentStorage storage,
                             CollaborationEvents events){
        this.attachments=attachments;this.records=records;this.projects=projects;this.members=members;
        this.users=users;this.storage=storage;this.events=events;
    }

    @Override public List<AttachmentDtos.View> list(UUID user,UUID recordId){
        RecordStore.RecordData record=record(recordId);requireMember(user,record);
        return attachments.findActiveByRecord(recordId).stream().map(value->view(value,user,record,true)).toList();
    }

    @Override @Transactional public AttachmentDtos.View upload(UUID user,UUID recordId,MultipartFile file){
        RecordStore.RecordData record=record(recordId);requireWritable(user,record);
        AttachmentStorage.StoredFile stored=storage.store(file);UUID id=UUID.randomUUID();Instant now=Instant.now();
        try{
            attachments.insert(new AttachmentStore.AttachmentRecord(id,recordId,user,stored.originalFilename(),
                    stored.storageKey(),stored.mediaType(),stored.sizeBytes(),stored.previewable(),now,null));
        }catch(RuntimeException e){storage.deleteQuietly(stored.storageKey());throw e;}
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)storage.deleteQuietly(stored.storageKey());}
        });
        events.audit(user,record.projectId(),recordId,"ATTACHMENT_UPLOADED","ATTACHMENT",id,
                Map.of("filename",stored.originalFilename(),"sizeBytes",stored.sizeBytes()));
        return getView(user,id,false);
    }

    @Override @Transactional public void delete(UUID user,UUID id){
        AttachmentStore.AttachmentRecord attachment=attachment(id,true);
        RecordStore.RecordData record=record(attachment.recordId());requireWritable(user,record);
        if(attachment.deletedAt()!=null)return;
        attachments.softDelete(id,Instant.now());
        events.audit(user,record.projectId(),record.id(),"ATTACHMENT_DELETED","ATTACHMENT",id,
                Map.of("filename",attachment.originalFilename()));
    }

    @Override public FilePayload load(UUID user,UUID id,boolean preview){
        AttachmentStore.AttachmentRecord attachment=attachment(id,false);
        RecordStore.RecordData record=record(attachment.recordId());requireMember(user,record);
        if(preview&&!isPreviewable(attachment.mediaType()))throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "PREVIEW_NOT_SUPPORTED","该文件类型仅支持下载");
        return new FilePayload(storage.read(attachment.storageKey()),attachment.originalFilename(),attachment.mediaType());
    }

    @Override public List<AttachmentDtos.View> revisionAttachments(UUID user,UUID revisionId){
        UUID project=attachments.findRevisionProjectId(revisionId)
                .orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","修订不存在"));
        if(members.findRole(project,user).isEmpty())
            throw new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","修订不存在或无权访问");
        return attachments.findByRevision(revisionId).stream().map(value->view(value,user,null,false)).toList();
    }

    private AttachmentDtos.View getView(UUID user,UUID id,boolean includeDeleted){
        AttachmentStore.AttachmentRecord attachment=attachment(id,includeDeleted);
        RecordStore.RecordData record=record(attachment.recordId());requireMember(user,record);
        return view(attachment,user,record,true);
    }

    private AttachmentDtos.View view(AttachmentStore.AttachmentRecord attachment,UUID user,
                                     RecordStore.RecordData record,boolean calculateWritable){
        String uploaderName=users.findById(attachment.uploaderId()).map(User::getDisplayName).orElse("");
        boolean writable=calculateWritable&&record!=null&&isWritable(user,record);
        return new AttachmentDtos.View(attachment.id(),attachment.recordId(),attachment.originalFilename(),
                attachment.mediaType(),attachment.sizeBytes(),isPreviewable(attachment.mediaType()),attachment.uploaderId(),
                uploaderName,attachment.createdAt(),attachment.deletedAt()!=null,writable);
    }

    private boolean isPreviewable(String mediaType) {
        if (mediaType == null) return false;
        return mediaType.startsWith("image/") || mediaType.equals("application/pdf")
                || mediaType.equals("text/markdown") || mediaType.equals("text/csv");
    }

    private AttachmentStore.AttachmentRecord attachment(UUID id,boolean includeDeleted){
        return attachments.findById(id,includeDeleted)
                .orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","附件不存在"));
    }
    private RecordStore.RecordData record(UUID id){
        return records.findActive(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","记录不存在"));
    }
    private void requireMember(UUID user,RecordStore.RecordData record){
        if(members.findRole(record.projectId(),user).isEmpty())
            throw new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","附件不存在或无权访问");
    }
    private boolean isWritable(UUID user,RecordStore.RecordData record){
        String projectStatus=projects.findById(record.projectId()).map(ProjectStore.ProjectRecord::status).orElse("");
        return record.creatorId().equals(user)&&"ACTIVE".equals(projectStatus)
                &&Set.of("IN_PROGRESS","CHANGES_REQUESTED").contains(record.status());
    }
    private void requireWritable(UUID user,RecordStore.RecordData record){
        requireMember(user,record);
        if(!isWritable(user,record))throw new ApiException(HttpStatus.CONFLICT,"RECORD_STATE_CONFLICT",
                "当前记录状态或权限不允许管理附件");
    }
}
