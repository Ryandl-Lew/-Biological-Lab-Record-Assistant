package com.bionote.project;

import com.bionote.common.PagedResponse;

import java.util.List;
import java.util.UUID;

/** HTTP-facing project collaboration application boundary. */
public interface ProjectUseCase {
    ProjectDtos.ProjectView create(UUID userId, ProjectDtos.CreateProjectRequest request);
    PagedResponse<ProjectDtos.ProjectView> list(UUID userId, String keyword, String status, int page, int size);
    ProjectDtos.ProjectView detail(UUID userId, UUID projectId);
    ProjectDtos.ProjectView archive(UUID userId, UUID projectId);
    List<ProjectDtos.MemberView> members(UUID userId, UUID projectId);
    ProjectDtos.InvitationView invite(UUID userId, UUID projectId, ProjectDtos.InviteRequest request);
    ProjectDtos.ProjectView accept(UUID userId, UUID invitationId);
    void reject(UUID userId, UUID invitationId);
    ProjectDtos.MemberView changeRole(UUID ownerId, UUID projectId, UUID target, ProjectDtos.RoleRequest request);
    void remove(UUID ownerId, UUID projectId, UUID target, ProjectDtos.RemoveRequest request);
}
