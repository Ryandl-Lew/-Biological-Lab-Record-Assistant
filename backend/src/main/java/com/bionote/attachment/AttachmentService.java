package com.bionote.attachment;

import com.bionote.collaboration.EventService;
import com.bionote.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class AttachmentService {
    private final JdbcTemplate jdbc; private final AttachmentStorageService storage; private final EventService events;
    public AttachmentService(JdbcTemplate jdbc,AttachmentStorageService storage,EventService events){this.jdbc=jdbc;this.storage=storage;this.events=events;}

    public List<AttachmentDtos.View> list(UUID user,UUID record){Map<String,Object> r=record(record);requireMember(user,r);return jdbc.query("SELECT a.*,u.display_name FROM attachments a JOIN users u ON u.id=a.uploader_id WHERE a.record_id=? AND a.deleted_at IS NULL ORDER BY a.created_at,a.id",(rs,n)->view(rs,user,r),record.toString());}

    @Transactional public AttachmentDtos.View upload(UUID user,UUID recordId,MultipartFile file){
        Map<String,Object> r=record(recordId);requireWritable(user,r);
        AttachmentStorageService.StoredFile stored=storage.store(file);UUID id=UUID.randomUUID();Instant now=Instant.now();
        try { jdbc.update("INSERT INTO attachments(id,record_id,uploader_id,original_filename,storage_key,media_type,size_bytes,previewable,created_at) VALUES(?,?,?,?,?,?,?,?,?)",id.toString(),recordId.toString(),user.toString(),stored.originalFilename(),stored.storageKey(),stored.mediaType(),stored.sizeBytes(),stored.previewable(),Timestamp.from(now)); }
        catch(RuntimeException e){storage.deleteQuietly(stored.storageKey());throw e;}
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)storage.deleteQuietly(stored.storageKey());}});
        events.audit(user,UUID.fromString(r.get("project_id").toString()),recordId,"ATTACHMENT_UPLOADED","ATTACHMENT",id,Map.of("filename",stored.originalFilename(),"sizeBytes",stored.sizeBytes()));
        return getView(user,id,false);
    }

    @Transactional public void delete(UUID user,UUID id){Map<String,Object>a=attachment(id,true);Map<String,Object>r=record(UUID.fromString(a.get("record_id").toString()));requireWritable(user,r);if(a.get("deleted_at")!=null)return;jdbc.update("UPDATE attachments SET deleted_at=? WHERE id=? AND deleted_at IS NULL",Timestamp.from(Instant.now()),id.toString());events.audit(user,UUID.fromString(r.get("project_id").toString()),UUID.fromString(r.get("id").toString()),"ATTACHMENT_DELETED","ATTACHMENT",id,Map.of("filename",a.get("original_filename")));}

    public FilePayload load(UUID user,UUID id,boolean preview){Map<String,Object>a=attachment(id,false);Map<String,Object>r=record(UUID.fromString(a.get("record_id").toString()));requireMember(user,r);if(preview&&!Boolean.TRUE.equals(a.get("previewable")))throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"PREVIEW_NOT_SUPPORTED","该文件类型仅支持下载");return new FilePayload(storage.read(a.get("storage_key").toString()),a.get("original_filename").toString(),a.get("media_type").toString());}

    public List<AttachmentDtos.View> revisionAttachments(UUID user,UUID revision){List<Map<String,Object>> rr=jdbc.queryForList("SELECT r.project_id FROM record_revisions rv JOIN experiment_records r ON r.id=rv.record_id WHERE rv.id=?",revision.toString());if(rr.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","修订不存在");UUID project=UUID.fromString(rr.get(0).get("project_id").toString());if(jdbc.queryForObject("SELECT COUNT(*) FROM project_members WHERE project_id=? AND user_id=?",Integer.class,project.toString(),user.toString())==0)throw new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","修订不存在或无权访问");return jdbc.query("SELECT a.*,u.display_name FROM revision_attachments ra JOIN attachments a ON a.id=ra.attachment_id JOIN users u ON u.id=a.uploader_id WHERE ra.revision_id=? ORDER BY ra.sort_order",(rs,n)->new AttachmentDtos.View(UUID.fromString(rs.getString("id")),UUID.fromString(rs.getString("record_id")),rs.getString("original_filename"),rs.getString("media_type"),rs.getLong("size_bytes"),rs.getBoolean("previewable"),UUID.fromString(rs.getString("uploader_id")),rs.getString("display_name"),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("deleted_at")!=null,false),revision.toString());}

    private AttachmentDtos.View getView(UUID user,UUID id,boolean allowDeleted){Map<String,Object>a=attachment(id,allowDeleted);Map<String,Object>r=record(UUID.fromString(a.get("record_id").toString()));requireMember(user,r);String name=jdbc.queryForObject("SELECT display_name FROM users WHERE id=?",String.class,a.get("uploader_id"));boolean writable=isWritable(user,r);return new AttachmentDtos.View(id,UUID.fromString(a.get("record_id").toString()),a.get("original_filename").toString(),a.get("media_type").toString(),((Number)a.get("size_bytes")).longValue(),Boolean.TRUE.equals(a.get("previewable")),UUID.fromString(a.get("uploader_id").toString()),name,((Timestamp)a.get("created_at")).toInstant(),a.get("deleted_at")!=null,writable);}
    private AttachmentDtos.View view(java.sql.ResultSet rs,UUID user,Map<String,Object>r)throws java.sql.SQLException{return new AttachmentDtos.View(UUID.fromString(rs.getString("id")),UUID.fromString(rs.getString("record_id")),rs.getString("original_filename"),rs.getString("media_type"),rs.getLong("size_bytes"),rs.getBoolean("previewable"),UUID.fromString(rs.getString("uploader_id")),rs.getString("display_name"),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("deleted_at")!=null,isWritable(user,r));}
    private Map<String,Object> attachment(UUID id,boolean includeDeleted){List<Map<String,Object>>rows=jdbc.queryForList("SELECT * FROM attachments WHERE id=?"+(includeDeleted?"":" AND (deleted_at IS NULL OR EXISTS(SELECT 1 FROM revision_attachments ra WHERE ra.attachment_id=attachments.id))"),id.toString());if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","附件不存在");return rows.get(0);}
    private Map<String,Object> record(UUID id){List<Map<String,Object>>rows=jdbc.queryForList("SELECT r.*,p.status project_status FROM experiment_records r JOIN projects p ON p.id=r.project_id WHERE r.id=? AND r.deleted_at IS NULL",id.toString());if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","记录不存在");return rows.get(0);}
    private void requireMember(UUID user,Map<String,Object>r){if(jdbc.queryForObject("SELECT COUNT(*) FROM project_members WHERE project_id=? AND user_id=?",Integer.class,r.get("project_id"),user.toString())==0)throw new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","附件不存在或无权访问");}
    private boolean isWritable(UUID user,Map<String,Object>r){return r.get("creator_id").toString().equals(user.toString())&&"ACTIVE".equals(r.get("project_status"))&&Set.of("IN_PROGRESS","CHANGES_REQUESTED").contains(r.get("status").toString());}
    private void requireWritable(UUID user,Map<String,Object>r){requireMember(user,r);if(!isWritable(user,r))throw new ApiException(HttpStatus.CONFLICT,"RECORD_STATE_CONFLICT","当前记录状态或权限不允许管理附件");}
    public record FilePayload(byte[] bytes,String filename,String mediaType){}
}
