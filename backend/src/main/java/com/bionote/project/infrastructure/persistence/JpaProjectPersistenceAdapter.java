package com.bionote.project.infrastructure.persistence;

import com.bionote.project.ProjectInvitationStore;
import com.bionote.project.ProjectMemberStore;
import com.bionote.project.ProjectStore;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaProjectPersistenceAdapter implements ProjectStore, ProjectMemberStore, ProjectInvitationStore {
    private final ProjectJpaRepository projects;
    private final ProjectMemberJpaRepository members;
    private final ProjectInvitationJpaRepository invitations;
    private final ProjectRecordReadRepository records;

    public JpaProjectPersistenceAdapter(ProjectJpaRepository projects, ProjectMemberJpaRepository members,
                                        ProjectInvitationJpaRepository invitations, ProjectRecordReadRepository records) {
        this.projects = projects;
        this.members = members;
        this.invitations = invitations;
        this.records = records;
    }

    @Override public void insert(ProjectRecord project) {
        projects.saveAndFlush(new ProjectEntity(project.id(), project.name(), project.description(),
                project.detailedDescription(), project.ownerId(), project.createdAt()));
    }

    @Override public Optional<ProjectRecord> findById(UUID projectId) { return projects.findById(projectId).map(this::map); }

    @Override public PageSlice searchForMember(UUID userId, String status, String escapedKeyword, int page, int size) {
        var result = projects.searchForMember(userId.toString(), status, escapedKeyword, PageRequest.of(page, size));
        return new PageSlice(result.getContent().stream().map(row -> new ProjectAccess(
                new ProjectRecord(UUID.fromString(row.getId()), row.getName(), row.getDescription(), row.getDetailedDescription(),
                        row.getStatus(), UUID.fromString(row.getOwnerId()), row.getCreatedAt(), row.getUpdatedAt(),
                        row.getArchivedAt(), row.getVersion()), row.getRole())).toList(), result.getTotalElements());
    }

    @Override public int archiveActive(UUID projectId, Instant now) { return projects.archiveActive(projectId, now); }
    @Override public long recordCount(UUID projectId) { return records.countByProjectIdAndDeletedAtIsNullAndProvisionalFalse(projectId); }

    @Override public void insert(UUID projectId, UUID userId, String role, Instant joinedAt, Instant lastActiveAt) {
        members.saveAndFlush(new ProjectMemberEntity(projectId, userId, role, joinedAt, lastActiveAt));
    }
    @Override public Optional<String> findRole(UUID projectId, UUID userId) {
        return members.findById(new ProjectMemberId(projectId, userId)).map(member -> member.role);
    }
    @Override public boolean exists(UUID projectId, UUID userId) { return members.existsById(new ProjectMemberId(projectId, userId)); }
    @Override public long count(UUID projectId) { return members.countByIdProjectId(projectId); }
    @Override public List<UUID> memberIds(UUID projectId) { return members.findMemberIds(projectId); }
    @Override public List<MemberRecord> list(UUID projectId) {
        return members.listMembers(projectId.toString()).stream().map(member -> new MemberRecord(
                UUID.fromString(member.getUserId()), member.getDisplayName(), member.getEmail(), member.getAvatarStorageKey(),
                member.getRole(), member.getJoinedAt(), member.getLastActiveAt())).toList();
    }
    @Override public Optional<MemberRecord> findMember(UUID projectId, UUID userId) {
        return list(projectId).stream().filter(member -> member.userId().equals(userId)).findFirst();
    }
    @Override public void touch(UUID projectId, UUID userId, Instant now) { members.touch(projectId, userId, now); }
    @Override public void updateRole(UUID projectId, UUID userId, String role) { members.updateRole(projectId, userId, role); }
    @Override public void remove(UUID projectId, UUID userId) { members.deleteById(new ProjectMemberId(projectId, userId)); members.flush(); }

    @Override public void insert(InvitationRecord invitation) {
        invitations.saveAndFlush(new ProjectInvitationEntity(invitation.id(), invitation.projectId(), invitation.inviterId(),
                invitation.inviteeUserId(), invitation.inviteeEmailSnapshot(), invitation.status(), invitation.expiresAt(),
                invitation.createdAt(), invitation.respondedAt(), invitation.pendingKey()));
    }
    @Override public Optional<InvitationRecord> findInvitation(UUID invitationId) { return invitations.findById(invitationId).map(this::map); }
    @Override public List<UUID> findPendingIds(UUID projectId) { return invitations.findPendingIds(projectId); }
    @Override public int expireForProject(UUID projectId, Instant now) { return invitations.expireForProject(projectId, now); }
    @Override public int expireDueForInvitee(UUID inviteeUserId, Instant now) { return invitations.expireDueForInvitee(inviteeUserId, now); }
    @Override public int changePendingStatus(UUID invitationId, String status, Instant respondedAt) {
        return invitations.changePendingStatus(invitationId, status, respondedAt);
    }

    List<ProjectRecordReadEntity> blockingArchive(UUID projectId) {
        return records.findByProjectIdAndDeletedAtIsNullAndProvisionalFalseAndStatusNot(projectId, "COMPLETED");
    }
    List<ProjectRecordReadEntity> blockingMember(UUID projectId, UUID userId) {
        return records.findByProjectIdAndCreatorIdAndDeletedAtIsNullAndProvisionalFalseAndStatusNot(projectId, userId, "COMPLETED");
    }

    private ProjectRecord map(ProjectEntity project) {
        return new ProjectRecord(project.id, project.name, project.description, project.detailedDescription, project.status,
                project.ownerId, project.createdAt, project.updatedAt, project.archivedAt, project.version);
    }
    private InvitationRecord map(ProjectInvitationEntity invitation) {
        return new InvitationRecord(invitation.id, invitation.projectId, invitation.inviterId, invitation.inviteeUserId,
                invitation.inviteeEmailSnapshot, invitation.status, invitation.expiresAt, invitation.createdAt,
                invitation.respondedAt, invitation.pendingKey);
    }
}
