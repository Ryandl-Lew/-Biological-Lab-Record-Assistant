package com.bionote.notification;

import com.bionote.common.ApiException;
import com.bionote.common.PagedResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class NotificationService {
    private final JdbcTemplate jdbc; private final ObjectMapper json;
    public NotificationService(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    public record View(UUID id,String type,String title,String body,Map<String,Object> target,List<String> actions,boolean stale,Instant createdAt,Instant readAt){}
    @Transactional public PagedResponse<View> list(UUID user,boolean unread,int page,int size){jdbc.update("UPDATE project_invitations SET status='EXPIRED',pending_key=NULL,responded_at=? WHERE invitee_user_id=? AND status='PENDING' AND expires_at<=?",Timestamp.from(Instant.now()),user.toString(),Timestamp.from(Instant.now()));page=Math.max(0,page);size=Math.max(1,Math.min(100,size));String extra=unread?" AND read_at IS NULL":"";long total=jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE recipient_id=?"+extra,Long.class,user.toString());List<View> items=jdbc.query("SELECT * FROM notifications WHERE recipient_id=?"+extra+" ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",(rs,n)->{Map<String,Object> payload;try{payload=json.readValue(rs.getString("payload_json"),new TypeReference<>(){});}catch(Exception e){payload=Map.of();}Resolved resolved=resolve(user,rs.getString("type"),payload);return new View(UUID.fromString(rs.getString("id")),rs.getString("type"),rs.getString("title"),rs.getString("body"),resolved.target(),resolved.actions(),resolved.stale(),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("read_at")==null?null:rs.getTimestamp("read_at").toInstant());},user.toString(),size,page*size);return PagedResponse.of(items,page,size,total);}
    @Transactional public void read(UUID user,UUID id){int changed=jdbc.update("UPDATE notifications SET read_at=COALESCE(read_at,?) WHERE id=? AND recipient_id=?",Timestamp.from(Instant.now()),id.toString(),user.toString());if(changed==0)throw new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","通知不存在");}
    @Transactional public void readAll(UUID user){jdbc.update("UPDATE notifications SET read_at=? WHERE recipient_id=? AND read_at IS NULL",Timestamp.from(Instant.now()),user.toString());}
    public long unread(UUID user){return jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE recipient_id=? AND read_at IS NULL",Long.class,user.toString());}
    private Resolved resolve(UUID user,String type,Map<String,Object> payload){String invitation=Objects.toString(payload.get("invitationId"),null),record=Objects.toString(payload.get("recordId"),null),project=Objects.toString(payload.get("projectId"),null),review=Objects.toString(payload.get("reviewId"),null);if(invitation!=null){List<Map<String,Object>>rows=jdbc.queryForList("SELECT i.status,i.invitee_user_id,i.expires_at,p.status project_status FROM project_invitations i JOIN projects p ON p.id=i.project_id WHERE i.id=?",invitation);boolean valid=!rows.isEmpty()&&rows.get(0).get("invitee_user_id").toString().equals(user.toString())&&"PENDING".equals(rows.get(0).get("status"))&&"ACTIVE".equals(rows.get(0).get("project_status"))&&((Timestamp)rows.get(0).get("expires_at")).toInstant().isAfter(Instant.now());return new Resolved(Map.of("type","INVITATION","id",invitation),valid?List.of("ACCEPT","REJECT"):List.of(),!valid);}if(record!=null){List<Map<String,Object>>rows=jdbc.queryForList("SELECT r.status,r.creator_id,r.current_review_id FROM experiment_records r JOIN project_members pm ON pm.project_id=r.project_id AND pm.user_id=? WHERE r.id=? AND r.deleted_at IS NULL",user.toString(),record);boolean access=!rows.isEmpty(),stale=!access;if(access&&Set.of("REVIEW_ASSIGNED","REVIEW_REASSIGNED").contains(type)){stale=review==null||!review.equals(Objects.toString(rows.get(0).get("current_review_id"),""))||jdbc.queryForObject("SELECT COUNT(*) FROM reviews WHERE id=? AND reviewer_id=? AND status='PENDING'",Integer.class,review,user.toString())==0;}if(access&&"CHANGES_REQUESTED".equals(type))stale=!rows.get(0).get("creator_id").toString().equals(user.toString())||!"CHANGES_REQUESTED".equals(rows.get(0).get("status"));return new Resolved(Map.of("type","RECORD","id",record),List.of(),stale);}if(project!=null){boolean access=jdbc.queryForObject("SELECT COUNT(*) FROM project_members WHERE project_id=? AND user_id=?",Integer.class,project,user.toString())>0;return new Resolved(Map.of("type","PROJECT","id",project),List.of(),!access);}return new Resolved(Map.of("type","NONE","id",""),List.of(),true);}
    private record Resolved(Map<String,Object> target,List<String> actions,boolean stale){}
}
