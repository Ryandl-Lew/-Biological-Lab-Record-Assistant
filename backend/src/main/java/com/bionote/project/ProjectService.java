package com.bionote.project;

import com.bionote.collaboration.CollaborationEvents;
import com.bionote.common.ApiException;
import com.bionote.common.PagedResponse;
import com.bionote.review.ReviewAssignmentUseCase;
import com.bionote.user.User;
import com.bionote.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ProjectService implements ProjectUseCase {
    private final ProjectStore projects;
    private final ProjectMemberStore members;
    private final ProjectInvitationStore invitations;
    private final UserRepository users;
    private final CollaborationEvents events;
    private final RecordMembershipGuard guard;
    private final ReviewAssignmentUseCase reviewAssignments;

    public ProjectService(ProjectStore projects, ProjectMemberStore members, ProjectInvitationStore invitations,
                          UserRepository users, CollaborationEvents events, RecordMembershipGuard guard,
                          ReviewAssignmentUseCase reviewAssignments) {
        this.projects = projects;
        this.members = members;
        this.invitations = invitations;
        this.users = users;
        this.events = events;
        this.guard = guard;
        this.reviewAssignments = reviewAssignments;
    }

    @Override @Transactional public ProjectDtos.ProjectView create(UUID userId, ProjectDtos.CreateProjectRequest request) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        projects.insert(new ProjectStore.ProjectRecord(id, request.name().trim(), trim(request.description()),
                trim(request.detailedDescription()), "ACTIVE", userId, now, now, null, 0));
        members.insert(id, userId, "OWNER", now, now);
        events.audit(userId, id, null, "PROJECT_CREATED", "PROJECT", id, Map.of("name", request.name().trim()));
        return detail(userId, id);
    }

    @Override public PagedResponse<ProjectDtos.ProjectView> list(UUID userId, String keyword, String status, int page, int size) {
        page = Math.max(0, page);
        size = Math.max(1, Math.min(100, size));
        String statusValue = status == null || status.isBlank() ? null : status;
        var result = projects.searchForMember(userId, statusValue, escape(keyword), page, size);
        List<ProjectDtos.ProjectView> items = result.items().stream().map(this::mapProject).toList();
        return PagedResponse.of(items, page, size, result.total());
    }

    @Override @Transactional public ProjectDtos.ProjectView detail(UUID userId, UUID projectId) {
        ProjectStore.ProjectRecord project = project(projectId);
        String role = members.findRole(projectId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "项目不存在或无权访问"));
        members.touch(projectId, userId, Instant.now());
        return mapProject(new ProjectStore.ProjectAccess(project, role));
    }

    @Override @Transactional public ProjectDtos.ProjectView archive(UUID userId, UUID projectId) {
        String role = requireMember(projectId, userId);
        requireOwner(role);
        ProjectStore.ProjectRecord project = project(projectId);
        if ("ARCHIVED".equals(project.status())) return detail(userId, projectId);
        var blockers = guard.recordsBlockingArchive(projectId);
        if (!blockers.isEmpty()) throw new ApiException(HttpStatus.CONFLICT, "PROJECT_ARCHIVE_BLOCKED",
                "仍有未完成记录，不能归档", Map.of("blockingRecords", blockers.toString()));
        Instant now = Instant.now();
        if (projects.archiveActive(projectId, now) == 0) return detail(userId, projectId);
        List<UUID> pending = invitations.findPendingIds(projectId);
        invitations.expireForProject(projectId, now);
        pending.forEach(id -> events.audit(userId, projectId, null, "INVITATION_EXPIRED", "INVITATION", id,
                Map.of("reason", "PROJECT_ARCHIVED")));
        members.memberIds(projectId).forEach(member -> events.notify(member, "PROJECT_ARCHIVED", "项目已归档",
                project.name() + " 已进入只读状态", Map.of("projectId", projectId.toString()),
                "project-archived:" + projectId + ":" + member));
        events.audit(userId, projectId, null, "PROJECT_ARCHIVED", "PROJECT", projectId, Map.of());
        return detail(userId, projectId);
    }

    @Override public List<ProjectDtos.MemberView> members(UUID userId, UUID projectId) {
        requireMember(projectId, userId);
        return members.list(projectId).stream().map(this::memberView).toList();
    }

    @Override @Transactional public ProjectDtos.InvitationView invite(UUID userId, UUID projectId, ProjectDtos.InviteRequest request) {
        requireActiveOwner(projectId, userId);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        User found = users.findByEmailNormalized(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "该邮箱尚未注册",
                        Map.of("email", "该邮箱尚未注册")));
        UUID invitee = found.getId();
        if (invitee.equals(userId)) throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", "不能邀请自己");
        if (members.exists(projectId, invitee))
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", "该用户已是项目成员");
        Instant now = Instant.now();
        invitations.expireDueForInvitee(invitee, now);
        UUID id = UUID.randomUUID();
        Instant expires = now.plus(7, ChronoUnit.DAYS);
        try {
            invitations.insert(new ProjectInvitationStore.InvitationRecord(id, projectId, userId, invitee, email,
                    "PENDING", expires, now, null, projectId + ":" + invitee));
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", "已有待处理邀请");
        }
        String projectName = project(projectId).name();
        events.notify(invitee, "PROJECT_INVITATION", "项目邀请", "你被邀请加入「" + projectName + "」",
                Map.of("invitationId", id.toString(), "projectId", projectId.toString(), "actions", List.of("ACCEPT", "REJECT")),
                "invitation:" + id);
        events.audit(userId, projectId, null, "INVITATION_CREATED", "INVITATION", id,
                Map.of("inviteeUserId", invitee.toString(), "email", email));
        return invitation(id);
    }

    @Override @Transactional public ProjectDtos.ProjectView accept(UUID userId, UUID invitationId) {
        ProjectInvitationStore.InvitationRecord invitation = invitationRecord(invitationId);
        validateInvitationActor(invitation, userId);
        expireIfNeeded(invitation, invitationId);
        if (!"PENDING".equals(invitation.status())) throw invitationStateConflict();
        UUID projectId = invitation.projectId();
        ProjectStore.ProjectRecord project = project(projectId);
        if ("ARCHIVED".equals(project.status())) {
            invitations.changePendingStatus(invitationId, "EXPIRED", Instant.now());
            throw new ApiException(HttpStatus.CONFLICT, "INVITATION_EXPIRED", "项目已归档，邀请失效");
        }
        Instant now = Instant.now();
        members.insert(projectId, userId, "MEMBER", now, now);
        if (invitations.changePendingStatus(invitationId, "ACCEPTED", now) == 0) throw invitationStateConflict();
        events.notify(project.ownerId(), "INVITATION_ACCEPTED", "邀请已接受",
                invitation.inviteeEmailSnapshot() + " 已加入项目", Map.of("projectId", projectId.toString()),
                "invitation-accepted:" + invitationId);
        events.audit(userId, projectId, null, "INVITATION_ACCEPTED", "INVITATION", invitationId, Map.of());
        return detail(userId, projectId);
    }

    @Override @Transactional public void reject(UUID userId, UUID invitationId) {
        ProjectInvitationStore.InvitationRecord invitation = invitationRecord(invitationId);
        validateInvitationActor(invitation, userId);
        expireIfNeeded(invitation, invitationId);
        if (!"PENDING".equals(invitation.status())) throw invitationStateConflict();
        if (invitations.changePendingStatus(invitationId, "REJECTED", Instant.now()) == 0) throw invitationStateConflict();
        ProjectStore.ProjectRecord project = project(invitation.projectId());
        events.notify(project.ownerId(), "INVITATION_REJECTED", "邀请已拒绝",
                invitation.inviteeEmailSnapshot() + " 拒绝了项目邀请",
                Map.of("projectId", invitation.projectId().toString()), "invitation-rejected:" + invitationId);
        events.audit(userId, invitation.projectId(), null, "INVITATION_REJECTED", "INVITATION", invitationId, Map.of());
    }

    @Override @Transactional public ProjectDtos.MemberView changeRole(UUID ownerId, UUID projectId, UUID target,
                                                                      ProjectDtos.RoleRequest request) {
        requireActiveOwner(projectId, ownerId);
        ProjectStore.ProjectRecord project = project(projectId);
        if (target.equals(project.ownerId())) throw new ApiException(HttpStatus.CONFLICT, "OWNER_IMMUTABLE", "项目负责人角色不可修改");
        String current = requireMember(projectId, target);
        if (current.equals(request.role())) return member(projectId, target);
        var blockers = guard.recordsBlockingMemberChange(projectId, target);
        if ("MEMBER".equals(current) && "REVIEWER".equals(request.role()) && !blockers.isEmpty())
            throw new ApiException(HttpStatus.CONFLICT, "MEMBER_ROLE_BLOCKED", "该成员仍有未完成记录",
                    Map.of("blockingRecords", blockers.toString()));
        if ("REVIEWER".equals(current) && "MEMBER".equals(request.role()))
            reviewAssignments.reassignPending(ownerId, projectId, target, request.reassignments());
        members.updateRole(projectId, target, request.role());
        events.notify(target, "PROJECT_ROLE_CHANGED", "项目角色已调整",
                "你在「" + project.name() + "」中的角色已变更为 " + request.role(),
                Map.of("projectId", projectId.toString(), "role", request.role()),
                "role:" + projectId + ":" + target + ":" + request.role());
        events.audit(ownerId, projectId, null, "MEMBER_ROLE_CHANGED", "USER", target,
                Map.of("from", current, "to", request.role()));
        return member(projectId, target);
    }

    @Override @Transactional public void remove(UUID ownerId, UUID projectId, UUID target, ProjectDtos.RemoveRequest request) {
        requireActiveOwner(projectId, ownerId);
        ProjectStore.ProjectRecord project = project(projectId);
        if (target.equals(project.ownerId())) throw new ApiException(HttpStatus.CONFLICT, "OWNER_IMMUTABLE", "不能移除项目负责人");
        String current = requireMember(projectId, target);
        var blockers = guard.recordsBlockingMemberChange(projectId, target);
        if (!blockers.isEmpty()) throw new ApiException(HttpStatus.CONFLICT, "MEMBER_REMOVE_BLOCKED", "成员仍有未完成记录",
                Map.of("blockingRecords", blockers.toString()));
        if ("REVIEWER".equals(current) || "OWNER".equals(current))
            reviewAssignments.reassignPending(ownerId, projectId, target, request.reassignments());
        members.remove(projectId, target);
        events.notify(target, "MEMBER_REMOVED", "已被移出项目", "你已被移出「" + project.name() + "」",
                Map.of("projectId", projectId.toString()), "removed:" + projectId + ":" + target);
        events.audit(ownerId, projectId, null, "MEMBER_REMOVED", "USER", target, Map.of());
    }

    private ProjectDtos.ProjectView mapProject(ProjectStore.ProjectAccess access) {
        ProjectStore.ProjectRecord project = access.project();
        String role = access.role();
        boolean active = "ACTIVE".equals(project.status());
        boolean owner = "OWNER".equals(role);
        return new ProjectDtos.ProjectView(project.id(), project.name(), project.description(), project.detailedDescription(),
                project.status(), project.ownerId(), role, project.createdAt(), project.updatedAt(), project.archivedAt(),
                project.version(), Map.of("canManage", active && owner, "canArchive", active && owner,
                        "canCreateRecord", active && !"REVIEWER".equals(role)), members.count(project.id()),
                projects.recordCount(project.id()));
    }

    private ProjectDtos.MemberView memberView(ProjectMemberStore.MemberRecord member) {
        return new ProjectDtos.MemberView(member.userId(), member.displayName(), member.email(),
                member.avatarStorageKey() == null ? null : "/api/v1/users/" + member.userId() + "/avatar",
                member.role(), member.joinedAt(), member.lastActiveAt(), Map.of(
                "canCreateRecord", !"REVIEWER".equals(member.role()),
                "canReview", !"MEMBER".equals(member.role())));
    }

    private ProjectDtos.MemberView member(UUID projectId, UUID userId) {
        return members.findMember(projectId, userId).map(this::memberView).orElseThrow();
    }

    private ProjectStore.ProjectRecord project(UUID id) {
        return projects.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "项目不存在"));
    }

    private ProjectInvitationStore.InvitationRecord invitationRecord(UUID id) {
        return invitations.findInvitation(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "邀请不存在"));
    }

    private ProjectDtos.InvitationView invitation(UUID id) {
        ProjectInvitationStore.InvitationRecord invitation = invitationRecord(id);
        return new ProjectDtos.InvitationView(id, invitation.projectId(), project(invitation.projectId()).name(),
                invitation.inviteeUserId(), invitation.inviteeEmailSnapshot(), invitation.status(),
                invitation.expiresAt(), invitation.createdAt());
    }

    private String requireMember(UUID projectId, UUID userId) {
        return members.findRole(projectId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "项目不存在或无权访问"));
    }

    private void requireOwner(String role) {
        if (!"OWNER".equals(role)) throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "仅项目负责人可执行此操作");
    }

    private void requireActiveOwner(UUID projectId, UUID userId) {
        requireOwner(requireMember(projectId, userId));
        if (!"ACTIVE".equals(project(projectId).status()))
            throw new ApiException(HttpStatus.CONFLICT, "PROJECT_ARCHIVED", "项目已归档，只能查看");
    }

    private void validateInvitationActor(ProjectInvitationStore.InvitationRecord invitation, UUID userId) {
        if (!invitation.inviteeUserId().equals(userId))
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "只能处理发给自己的邀请");
    }

    private void expireIfNeeded(ProjectInvitationStore.InvitationRecord invitation, UUID id) {
        if ("PENDING".equals(invitation.status()) && !invitation.expiresAt().isAfter(Instant.now())) {
            invitations.changePendingStatus(id, "EXPIRED", Instant.now());
            throw new ApiException(HttpStatus.CONFLICT, "INVITATION_EXPIRED", "邀请已过期");
        }
    }

    private ApiException invitationStateConflict() {
        return new ApiException(HttpStatus.CONFLICT, "INVITATION_STATE_CONFLICT", "邀请已处理或失效");
    }

    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String escape(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
