package com.bionote.project;

import com.bionote.collaboration.EventService;
import com.bionote.common.ApiException;
import com.bionote.common.PagedResponse;
import com.bionote.review.ReviewAssignmentService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class ProjectService {
    private final JdbcTemplate jdbc; private final EventService events; private final RecordMembershipGuard guard; private final ReviewAssignmentService reviewAssignments;
    public ProjectService(JdbcTemplate jdbc, EventService events, RecordMembershipGuard guard,ReviewAssignmentService reviewAssignments){this.jdbc=jdbc;this.events=events;this.guard=guard;this.reviewAssignments=reviewAssignments;}
    private String s(UUID id){return id.toString();}
    private Instant instant(Timestamp t){return t==null?null:t.toInstant();}
    private int page(int value){return Math.max(0,value);} private int size(int value){return Math.max(1,Math.min(100,value));}

    @Transactional public ProjectDtos.ProjectView create(UUID userId, ProjectDtos.CreateProjectRequest request){
        UUID id=UUID.randomUUID(); Instant now=Instant.now();
        jdbc.update("INSERT INTO projects(id,name,description,detailed_description,status,owner_id,created_at,updated_at,version) VALUES(?,?,?,?,?,?,?,?,0)",
                s(id),request.name().trim(),trim(request.description()),trim(request.detailedDescription()),"ACTIVE",s(userId),Timestamp.from(now),Timestamp.from(now));
        jdbc.update("INSERT INTO project_members(project_id,user_id,role,joined_at,last_active_at) VALUES(?,?,?,?,?)",s(id),s(userId),"OWNER",Timestamp.from(now),Timestamp.from(now));
        events.audit(userId,id,null,"PROJECT_CREATED","PROJECT",id,Map.of("name",request.name().trim()));
        return detail(userId,id);
    }

    public PagedResponse<ProjectDtos.ProjectView> list(UUID userId, String keyword, String status, int page, int size){
        page=page(page);size=size(size); String like="%"+escape(keyword)+"%";
        String statusValue=status==null||status.isBlank()?null:status;
        String where=" FROM projects p JOIN project_members pm ON pm.project_id=p.id WHERE pm.user_id=? AND (? IS NULL OR p.status=?) AND (LOWER(p.name) LIKE ? ESCAPE '!' OR LOWER(COALESCE(p.description,'')) LIKE ? ESCAPE '!' OR LOWER(COALESCE(p.detailed_description,'')) LIKE ? ESCAPE '!')";
        long total=jdbc.queryForObject("SELECT COUNT(*)"+where,Long.class,s(userId),statusValue,statusValue,like,like,like);
        List<ProjectDtos.ProjectView> items=jdbc.query("SELECT p.*,pm.role"+where+" ORDER BY p.updated_at DESC,p.id DESC LIMIT ? OFFSET ?",
                (rs,n)->mapProject(rs,userId),s(userId),statusValue,statusValue,like,like,like,size,page*size);
        return PagedResponse.of(items,page,size,total);
    }

    public ProjectDtos.ProjectView detail(UUID userId, UUID projectId){
        List<ProjectDtos.ProjectView> rows=jdbc.query("SELECT p.*,pm.role FROM projects p JOIN project_members pm ON pm.project_id=p.id WHERE p.id=? AND pm.user_id=?",
                (rs,n)->mapProject(rs,userId),s(projectId),s(userId));
        if(rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","项目不存在或无权访问");
        jdbc.update("UPDATE project_members SET last_active_at=? WHERE project_id=? AND user_id=?",Timestamp.from(Instant.now()),s(projectId),s(userId));
        return rows.get(0);
    }

    @Transactional public ProjectDtos.ProjectView archive(UUID userId, UUID projectId){
        String role=requireMember(projectId,userId); requireOwner(role);
        Map<String,Object> project=projectRow(projectId);
        if("ARCHIVED".equals(project.get("status"))) return detail(userId,projectId);
        var blockers=guard.recordsBlockingArchive(projectId);
        if(!blockers.isEmpty()) throw new ApiException(HttpStatus.CONFLICT,"PROJECT_ARCHIVE_BLOCKED","仍有未完成记录，不能归档",Map.of("blockingRecords",blockers.toString()));
        Instant now=Instant.now();
        jdbc.update("UPDATE projects SET status='ARCHIVED',archived_at=?,updated_at=?,version=version+1 WHERE id=? AND status='ACTIVE'",Timestamp.from(now),Timestamp.from(now),s(projectId));
        List<String> pending=jdbc.queryForList("SELECT id FROM project_invitations WHERE project_id=? AND status='PENDING'",String.class,s(projectId));
        jdbc.update("UPDATE project_invitations SET status='EXPIRED',pending_key=NULL,responded_at=? WHERE project_id=? AND status='PENDING'",Timestamp.from(now),s(projectId));
        pending.forEach(id->events.audit(userId,projectId,null,"INVITATION_EXPIRED","INVITATION",UUID.fromString(id),Map.of("reason","PROJECT_ARCHIVED")));
        jdbc.queryForList("SELECT user_id FROM project_members WHERE project_id=?",String.class,s(projectId)).forEach(member ->
                events.notify(UUID.fromString(member),"PROJECT_ARCHIVED","项目已归档",project.get("name")+" 已进入只读状态",Map.of("projectId",s(projectId)),"project-archived:"+projectId+":"+member));
        events.audit(userId,projectId,null,"PROJECT_ARCHIVED","PROJECT",projectId,Map.of());
        return detail(userId,projectId);
    }

    public List<ProjectDtos.MemberView> members(UUID userId, UUID projectId){
        requireMember(projectId,userId);
        return jdbc.query("SELECT pm.*,u.display_name,u.email_normalized,u.avatar_storage_key FROM project_members pm JOIN users u ON u.id=pm.user_id WHERE pm.project_id=? ORDER BY CASE pm.role WHEN 'OWNER' THEN 0 WHEN 'REVIEWER' THEN 1 ELSE 2 END,u.display_name",
                (rs,n)->new ProjectDtos.MemberView(UUID.fromString(rs.getString("user_id")),rs.getString("display_name"),rs.getString("email_normalized"),
                        rs.getString("avatar_storage_key")==null?null:"/api/v1/users/"+rs.getString("user_id")+"/avatar",rs.getString("role"),instant(rs.getTimestamp("joined_at")),instant(rs.getTimestamp("last_active_at")),
                        Map.of("canCreateRecord",!"REVIEWER".equals(rs.getString("role")),"canReview",!"MEMBER".equals(rs.getString("role")))),s(projectId));
    }

    @Transactional public ProjectDtos.InvitationView invite(UUID userId, UUID projectId, ProjectDtos.InviteRequest request){
        requireActiveOwner(projectId,userId); String email=request.email().trim().toLowerCase(Locale.ROOT);
        List<Map<String,Object>> found=jdbc.queryForList("SELECT id,email_normalized FROM users WHERE email_normalized=?",email);
        if(found.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","该邮箱尚未注册",Map.of("email","该邮箱尚未注册"));
        UUID invitee=UUID.fromString(found.get(0).get("id").toString());
        if(invitee.equals(userId)) throw new ApiException(HttpStatus.CONFLICT,"DUPLICATE_RESOURCE","不能邀请自己");
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT COUNT(*)>0 FROM project_members WHERE project_id=? AND user_id=?",Boolean.class,s(projectId),s(invitee))))
            throw new ApiException(HttpStatus.CONFLICT,"DUPLICATE_RESOURCE","该用户已是项目成员");
        expireDueInvitations(invitee);
        UUID id=UUID.randomUUID(); Instant now=Instant.now(),expires=now.plus(7,ChronoUnit.DAYS);
        try { jdbc.update("INSERT INTO project_invitations(id,project_id,inviter_id,invitee_user_id,invitee_email_snapshot,status,expires_at,created_at,pending_key) VALUES(?,?,?,?,?,'PENDING',?,?,?)",
                s(id),s(projectId),s(userId),s(invitee),email,Timestamp.from(expires),Timestamp.from(now),projectId+":"+invitee); }
        catch(DuplicateKeyException ex){throw new ApiException(HttpStatus.CONFLICT,"DUPLICATE_RESOURCE","已有待处理邀请");}
        String projectName=projectRow(projectId).get("name").toString();
        events.notify(invitee,"PROJECT_INVITATION","项目邀请","你被邀请加入「"+projectName+"」",Map.of("invitationId",s(id),"projectId",s(projectId),"actions",List.of("ACCEPT","REJECT")),"invitation:"+id);
        events.audit(userId,projectId,null,"INVITATION_CREATED","INVITATION",id,Map.of("inviteeUserId",s(invitee),"email",email));
        return invitation(id);
    }

    @Transactional public ProjectDtos.ProjectView accept(UUID userId, UUID invitationId){
        Map<String,Object> row=invitationRow(invitationId); validateInvitationActor(row,userId); expireIfNeeded(row,invitationId);
        if(!"PENDING".equals(row.get("status"))) throw new ApiException(HttpStatus.CONFLICT,"INVITATION_STATE_CONFLICT","邀请已处理或失效");
        UUID projectId=UUID.fromString(row.get("project_id").toString());
        if("ARCHIVED".equals(projectRow(projectId).get("status"))) { expireInvitation(invitationId); throw new ApiException(HttpStatus.CONFLICT,"INVITATION_EXPIRED","项目已归档，邀请失效"); }
        Instant now=Instant.now();
        jdbc.update("INSERT INTO project_members(project_id,user_id,role,joined_at,last_active_at) VALUES(?,?, 'MEMBER',?,?)",s(projectId),s(userId),Timestamp.from(now),Timestamp.from(now));
        jdbc.update("UPDATE project_invitations SET status='ACCEPTED',pending_key=NULL,responded_at=? WHERE id=? AND status='PENDING'",Timestamp.from(now),s(invitationId));
        UUID owner=UUID.fromString(projectRow(projectId).get("owner_id").toString());
        events.notify(owner,"INVITATION_ACCEPTED","邀请已接受",row.get("invitee_email_snapshot")+" 已加入项目",Map.of("projectId",s(projectId)),"invitation-accepted:"+invitationId);
        events.audit(userId,projectId,null,"INVITATION_ACCEPTED","INVITATION",invitationId,Map.of());
        return detail(userId,projectId);
    }

    @Transactional public void reject(UUID userId, UUID invitationId){
        Map<String,Object> row=invitationRow(invitationId); validateInvitationActor(row,userId); expireIfNeeded(row,invitationId);
        if(!"PENDING".equals(row.get("status"))) throw new ApiException(HttpStatus.CONFLICT,"INVITATION_STATE_CONFLICT","邀请已处理或失效");
        Instant now=Instant.now(); jdbc.update("UPDATE project_invitations SET status='REJECTED',pending_key=NULL,responded_at=? WHERE id=?",Timestamp.from(now),s(invitationId));
        UUID projectId=UUID.fromString(row.get("project_id").toString()), owner=UUID.fromString(projectRow(projectId).get("owner_id").toString());
        events.notify(owner,"INVITATION_REJECTED","邀请已拒绝",row.get("invitee_email_snapshot")+" 拒绝了项目邀请",Map.of("projectId",s(projectId)),"invitation-rejected:"+invitationId);
        events.audit(userId,projectId,null,"INVITATION_REJECTED","INVITATION",invitationId,Map.of());
    }

    @Transactional public ProjectDtos.MemberView changeRole(UUID ownerId, UUID projectId, UUID target, ProjectDtos.RoleRequest request){
        requireActiveOwner(projectId,ownerId); Map<String,Object> project=projectRow(projectId);
        if(target.toString().equals(project.get("owner_id").toString())) throw new ApiException(HttpStatus.CONFLICT,"OWNER_IMMUTABLE","项目负责人角色不可修改");
        String current=requireMember(projectId,target);
        if(current.equals(request.role())) return member(projectId,target);
        var blockers=guard.recordsBlockingMemberChange(projectId,target);
        if("MEMBER".equals(current)&&"REVIEWER".equals(request.role())&&!blockers.isEmpty())
            throw new ApiException(HttpStatus.CONFLICT,"MEMBER_ROLE_BLOCKED","该成员仍有未完成记录",Map.of("blockingRecords",blockers.toString()));
        if("REVIEWER".equals(current)&&"MEMBER".equals(request.role())) reviewAssignments.reassignPending(ownerId,projectId,target,request.reassignments());
        jdbc.update("UPDATE project_members SET role=? WHERE project_id=? AND user_id=?",request.role(),s(projectId),s(target));
        events.notify(target,"PROJECT_ROLE_CHANGED","项目角色已调整","你在「"+project.get("name")+"」中的角色已变更为 "+request.role(),Map.of("projectId",s(projectId),"role",request.role()),"role:"+projectId+":"+target+":"+request.role());
        events.audit(ownerId,projectId,null,"MEMBER_ROLE_CHANGED","USER",target,Map.of("from",current,"to",request.role()));
        return member(projectId,target);
    }

    @Transactional public void remove(UUID ownerId, UUID projectId, UUID target, ProjectDtos.RemoveRequest request){
        requireActiveOwner(projectId,ownerId); Map<String,Object> project=projectRow(projectId);
        if(target.toString().equals(project.get("owner_id").toString())) throw new ApiException(HttpStatus.CONFLICT,"OWNER_IMMUTABLE","不能移除项目负责人");
        String current=requireMember(projectId,target); var blockers=guard.recordsBlockingMemberChange(projectId,target);
        if(!blockers.isEmpty()) throw new ApiException(HttpStatus.CONFLICT,"MEMBER_REMOVE_BLOCKED","成员仍有未完成记录",Map.of("blockingRecords",blockers.toString()));
        if("REVIEWER".equals(current)||"OWNER".equals(current)) reviewAssignments.reassignPending(ownerId,projectId,target,request.reassignments());
        jdbc.update("DELETE FROM project_members WHERE project_id=? AND user_id=?",s(projectId),s(target));
        events.notify(target,"MEMBER_REMOVED","已被移出项目","你已被移出「"+project.get("name")+"」",Map.of("projectId",s(projectId)),"removed:"+projectId+":"+target);
        events.audit(ownerId,projectId,null,"MEMBER_REMOVED","USER",target,Map.of());
    }

    private ProjectDtos.ProjectView mapProject(ResultSet rs, UUID userId) throws SQLException {
        UUID id=UUID.fromString(rs.getString("id")); String role=rs.getString("role"),status=rs.getString("status");
        long members=jdbc.queryForObject("SELECT COUNT(*) FROM project_members WHERE project_id=?",Long.class,s(id));
        long records=tableExists("EXPERIMENT_RECORDS")?jdbc.queryForObject("SELECT COUNT(*) FROM experiment_records WHERE project_id=? AND deleted_at IS NULL AND provisional=FALSE",Long.class,s(id)):0;
        boolean active="ACTIVE".equals(status), owner="OWNER".equals(role);
        return new ProjectDtos.ProjectView(id,rs.getString("name"),rs.getString("description"),rs.getString("detailed_description"),status,UUID.fromString(rs.getString("owner_id")),role,
                instant(rs.getTimestamp("created_at")),instant(rs.getTimestamp("updated_at")),instant(rs.getTimestamp("archived_at")),rs.getLong("version"),
                Map.of("canManage",active&&owner,"canArchive",active&&owner,"canCreateRecord",active&&!"REVIEWER".equals(role)),members,records);
    }
    private boolean tableExists(String name){try{return Boolean.TRUE.equals(jdbc.queryForObject("SELECT COUNT(*)>0 FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME)=?",Boolean.class,name));}catch(Exception e){return false;}}
    private ProjectDtos.MemberView member(UUID project,UUID user){return members(user,project).stream().filter(m->m.userId().equals(user)).findFirst().orElseThrow();}
    private Map<String,Object> projectRow(UUID id){List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM projects WHERE id=?",s(id));if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","项目不存在");return rows.get(0);}
    private Map<String,Object> invitationRow(UUID id){List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM project_invitations WHERE id=?",s(id));if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","邀请不存在");return rows.get(0);}
    private ProjectDtos.InvitationView invitation(UUID id){Map<String,Object> row=invitationRow(id);Map<String,Object> p=projectRow(UUID.fromString(row.get("project_id").toString()));return new ProjectDtos.InvitationView(id,UUID.fromString(row.get("project_id").toString()),p.get("name").toString(),UUID.fromString(row.get("invitee_user_id").toString()),row.get("invitee_email_snapshot").toString(),row.get("status").toString(),((Timestamp)row.get("expires_at")).toInstant(),((Timestamp)row.get("created_at")).toInstant());}
    private String requireMember(UUID project,UUID user){List<String> roles=jdbc.queryForList("SELECT role FROM project_members WHERE project_id=? AND user_id=?",String.class,s(project),s(user));if(roles.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","项目不存在或无权访问");return roles.get(0);}
    private void requireOwner(String role){if(!"OWNER".equals(role))throw new ApiException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","仅项目负责人可执行此操作");}
    private void requireActiveOwner(UUID project,UUID user){requireOwner(requireMember(project,user));if(!"ACTIVE".equals(projectRow(project).get("status")))throw new ApiException(HttpStatus.CONFLICT,"PROJECT_ARCHIVED","项目已归档，只能查看");}
    private void validateInvitationActor(Map<String,Object> row,UUID user){if(!row.get("invitee_user_id").toString().equals(s(user)))throw new ApiException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","只能处理发给自己的邀请");}
    private void expireIfNeeded(Map<String,Object> row,UUID id){if("PENDING".equals(row.get("status"))&&!((Timestamp)row.get("expires_at")).toInstant().isAfter(Instant.now())){expireInvitation(id);throw new ApiException(HttpStatus.CONFLICT,"INVITATION_EXPIRED","邀请已过期");}}
    private void expireInvitation(UUID id){jdbc.update("UPDATE project_invitations SET status='EXPIRED',pending_key=NULL,responded_at=? WHERE id=? AND status='PENDING'",Timestamp.from(Instant.now()),s(id));}
    private void expireDueInvitations(UUID user){jdbc.update("UPDATE project_invitations SET status='EXPIRED',pending_key=NULL,responded_at=? WHERE invitee_user_id=? AND status='PENDING' AND expires_at<=?",Timestamp.from(Instant.now()),s(user),Timestamp.from(Instant.now()));}
    private String trim(String value){return value==null||value.isBlank()?null:value.trim();}
    private String escape(String value){return value==null?"":value.trim().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_");}
}
