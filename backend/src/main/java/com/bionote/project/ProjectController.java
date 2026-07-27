package com.bionote.project;

import com.bionote.common.ApiResponse;
import com.bionote.common.PagedResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ProjectController {
    private final ProjectService service; public ProjectController(ProjectService service){this.service=service;}
    private UUID current(Authentication a){return UUID.fromString(a.getName());}
    @GetMapping("/projects") PagedResponse<ProjectDtos.ProjectView> list(Authentication a,@RequestParam(defaultValue="") String keyword,@RequestParam(required=false) String status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.list(current(a),keyword,status,page,size);}
    @PostMapping("/projects") ApiResponse<ProjectDtos.ProjectView> create(Authentication a,@Valid @RequestBody ProjectDtos.CreateProjectRequest r){return ApiResponse.of(service.create(current(a),r));}
    @GetMapping("/projects/{id}") ApiResponse<ProjectDtos.ProjectView> detail(Authentication a,@PathVariable UUID id){return ApiResponse.of(service.detail(current(a),id));}
    @PostMapping("/projects/{id}/archive") ApiResponse<ProjectDtos.ProjectView> archive(Authentication a,@PathVariable UUID id){return ApiResponse.of(service.archive(current(a),id));}
    @GetMapping("/projects/{id}/members") ApiResponse<List<ProjectDtos.MemberView>> members(Authentication a,@PathVariable UUID id){return ApiResponse.of(service.members(current(a),id));}
    @PostMapping("/projects/{id}/invitations") ApiResponse<ProjectDtos.InvitationView> invite(Authentication a,@PathVariable UUID id,@Valid @RequestBody ProjectDtos.InviteRequest r){return ApiResponse.of(service.invite(current(a),id,r));}
    @PostMapping("/invitations/{id}/accept") ApiResponse<ProjectDtos.ProjectView> accept(Authentication a,@PathVariable UUID id){return ApiResponse.of(service.accept(current(a),id));}
    @PostMapping("/invitations/{id}/reject") ResponseEntity<Void> reject(Authentication a,@PathVariable UUID id){service.reject(current(a),id);return ResponseEntity.noContent().build();}
    @PatchMapping("/projects/{projectId}/members/{userId}/role") ApiResponse<ProjectDtos.MemberView> role(Authentication a,@PathVariable UUID projectId,@PathVariable UUID userId,@Valid @RequestBody ProjectDtos.RoleRequest r){return ApiResponse.of(service.changeRole(current(a),projectId,userId,r));}
    @PostMapping("/projects/{projectId}/members/{userId}/remove") ResponseEntity<Void> remove(Authentication a,@PathVariable UUID projectId,@PathVariable UUID userId,@RequestBody(required=false) ProjectDtos.RemoveRequest r){service.remove(current(a),projectId,userId,r==null?new ProjectDtos.RemoveRequest(null):r);return ResponseEntity.noContent().build();}
}

