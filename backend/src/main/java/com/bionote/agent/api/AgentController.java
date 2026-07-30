package com.bionote.agent.api;

import com.bionote.common.ApiResponse;
import com.bionote.common.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(
        name = "Agent Runs and Artifacts",
        description =
                "Context-bound report runs, validated artifacts, trace replay, cancellation and rerun")
public class AgentController {
    private static final Logger log = LoggerFactory.getLogger(AgentController.class);
    private final AgentRunUseCase service;

    public AgentController(AgentRunUseCase service) {
        this.service = service;
    }

    @Operation(summary = "创建记录总结 Run", description = "仅记录创建者可创建；返回 202，模型与工具不在 HTTP 线程执行。")
    @PostMapping("/records/{recordId}/agent-runs")
    ResponseEntity<ApiResponse<AgentDtos.RunView>> createRecord(
            Authentication a,
            @PathVariable UUID recordId,
            @Valid @RequestBody AgentDtos.CreateRunRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        log.info(
                "Agent run request received: type=record recordId={} userId={} artifactKind={}",
                recordId,
                a.getName(),
                request.artifactKind());
        return ResponseEntity.accepted()
                .body(ApiResponse.of(service.createRecord(current(a), recordId, request, key)));
    }

    @Operation(summary = "创建项目进展 Run", description = "仅项目 OWNER 可创建；归档项目仍允许生成只读报告。")
    @PostMapping("/projects/{projectId}/agent-runs")
    ResponseEntity<ApiResponse<AgentDtos.RunView>> createProject(
            Authentication a,
            @PathVariable UUID projectId,
            @Valid @RequestBody AgentDtos.CreateRunRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        log.info(
                "Agent run request received: type=project projectId={} userId={} artifactKind={}",
                projectId,
                a.getName(),
                request.artifactKind());
        return ResponseEntity.accepted()
                .body(ApiResponse.of(service.createProject(current(a), projectId, request, key)));
    }

    @Operation(summary = "查询 Agent Run")
    @GetMapping("/agent-runs/{runId}")
    ApiResponse<AgentDtos.RunView> run(Authentication a, @PathVariable UUID runId) {
        return ApiResponse.of(service.get(current(a), runId));
    }

    @Operation(summary = "分页查询脱敏 Trace steps", description = "仅请求者或项目 OWNER 可查看；不包含隐藏推理。")
    @GetMapping("/agent-runs/{runId}/steps")
    PagedResponse<AgentDtos.StepView> steps(
            Authentication a,
            @PathVariable UUID runId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.steps(current(a), runId, page, size);
    }

    @Operation(summary = "取消 Agent Run")
    @PostMapping("/agent-runs/{runId}/cancel")
    ApiResponse<AgentDtos.RunView> cancel(Authentication a, @PathVariable UUID runId) {
        return ApiResponse.of(service.cancel(current(a), runId));
    }

    @Operation(summary = "基于原请求重新运行", description = "创建新的子 Run，保留旧 Run、Artifact 和 Trace。")
    @PostMapping("/agent-runs/{runId}/rerun")
    ResponseEntity<ApiResponse<AgentDtos.RunView>> rerun(
            Authentication a,
            @PathVariable UUID runId,
            @RequestHeader("Idempotency-Key") String key) {
        return ResponseEntity.accepted()
                .body(ApiResponse.of(service.rerun(current(a), runId, key)));
    }

    @Operation(summary = "分页查询记录总结 Artifact")
    @GetMapping("/records/{recordId}/agent-artifacts")
    PagedResponse<AgentDtos.ArtifactSummary> recordArtifacts(
            Authentication a,
            @PathVariable UUID recordId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.recordArtifacts(current(a), recordId, page, size);
    }

    @Operation(summary = "分页查询项目进展 Artifact")
    @GetMapping("/projects/{projectId}/agent-artifacts")
    PagedResponse<AgentDtos.ArtifactSummary> projectArtifacts(
            Authentication a,
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.projectArtifacts(current(a), projectId, page, size);
    }

    @Operation(summary = "查询已验证 Artifact 详情", description = "读取时重新检查当前项目成员权限。")
    @GetMapping("/agent-artifacts/{artifactId}")
    ApiResponse<AgentDtos.ArtifactView> artifact(Authentication a, @PathVariable UUID artifactId) {
        return ApiResponse.of(service.artifact(current(a), artifactId));
    }

    private UUID current(Authentication a) {
        return UUID.fromString(a.getName());
    }
}
