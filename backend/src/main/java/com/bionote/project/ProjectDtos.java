package com.bionote.project;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ProjectDtos {
    private ProjectDtos() {}
    public record CreateProjectRequest(@NotBlank @Size(max=120) String name,
                                       @Size(max=500) String description,
                                       @Size(max=10000) String detailedDescription) {}
    public record InviteRequest(@NotBlank @Email String email) {}
    public record RoleRequest(@NotBlank @Pattern(regexp="MEMBER|REVIEWER") String role, Map<UUID,UUID> reassignments) {}
    public record RemoveRequest(Map<UUID,UUID> reassignments) {}
    public record ProjectView(UUID id, String name, String description, String detailedDescription, String status, UUID ownerId,
                              String currentUserRole, Instant createdAt, Instant updatedAt, Instant archivedAt,
                              long version, Map<String,Boolean> capabilities, long memberCount, long recordCount) {}
    public record MemberView(UUID userId, String displayName, String email, String avatarUrl, String role,
                             Instant joinedAt, Instant lastActiveAt, Map<String,Boolean> permissions) {}
    public record InvitationView(UUID id, UUID projectId, String projectName, UUID inviteeUserId, String inviteeEmail,
                                 String status, Instant expiresAt, Instant createdAt) {}
    public record BlockingResponse(String message, List<RecordMembershipGuard.BlockingItem> blockingRecords) {}
}
