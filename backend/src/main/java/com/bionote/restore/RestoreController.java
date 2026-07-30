package com.bionote.restore;

import com.bionote.common.ApiResponse;
import com.bionote.common.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
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
@RequestMapping("/api/v1/records/{recordId}")
@Tag(
        name = "Record Restore",
        description = "HMAC-bound restore preview and transactional working-copy restore")
public class RestoreController {
    private final RestorePreviewUseCase previews;
    private final RestoreExecutionUseCase executions;
    private final RestoreHistoryUseCase operations;

    public RestoreController(
            RestorePreviewUseCase previews,
            RestoreExecutionUseCase executions,
            RestoreHistoryUseCase operations) {
        this.previews = previews;
        this.executions = executions;
        this.operations = operations;
    }

    @Operation(summary = "预览恢复", description = "计算结构化 Diff、附件计划和短时效 HMAC token，不修改记录。")
    @PostMapping("/restore-preview")
    ApiResponse<RestoreDtos.Preview> preview(
            Authentication auth,
            @PathVariable UUID recordId,
            @Valid @RequestBody RestoreDtos.PreviewRequest request) {
        return ApiResponse.of(previews.preview(current(auth), recordId, request));
    }

    @Operation(summary = "执行恢复", description = "在单事务中重新授权、锁行、校验乐观锁与幂等键，只修改 Working Copy。")
    @PostMapping("/restore")
    ApiResponse<RestoreDtos.Result> restore(
            Authentication auth,
            @PathVariable UUID recordId,
            @Valid @RequestBody RestoreDtos.ExecuteRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.of(executions.restore(current(auth), recordId, request, key));
    }

    @Operation(summary = "分页查询恢复历史")
    @GetMapping("/restore-operations")
    PagedResponse<RestoreDtos.OperationSummary> history(
            Authentication auth,
            @PathVariable UUID recordId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return operations.list(current(auth), recordId, page, size);
    }

    private UUID current(Authentication auth) {
        return UUID.fromString(auth.getName());
    }
}
