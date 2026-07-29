package com.bionote.revision;

import com.bionote.common.ApiResponse;
import com.bionote.common.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class RevisionController {
    private final RevisionQueryUseCase queries;
    private final RevisionDiffUseCase diffs;
    public RevisionController(RevisionQueryUseCase queries, RevisionDiffUseCase diffs) { this.queries = queries; this.diffs = diffs; }

    @Operation(summary = "分页查询记录修订摘要", description = "项目参与者可用；列表不返回完整快照或正文。")
    @GetMapping("/records/{recordId}/revisions")
    PagedResponse<RevisionDtos.RevisionSummary> list(Authentication authentication, @PathVariable UUID recordId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return queries.list(current(authentication), recordId, page, size);
    }

    @Operation(summary = "查询记录修订详情")
    @GetMapping("/records/{recordId}/revisions/{revisionId}")
    ApiResponse<RevisionDtos.RevisionDetail> detail(Authentication authentication, @PathVariable UUID recordId,
                                                     @PathVariable UUID revisionId) {
        return ApiResponse.of(queries.detail(current(authentication), recordId, revisionId));
    }

    @Operation(summary = "兼容旧路径查询修订详情")
    @GetMapping("/revisions/{revisionId}")
    ApiResponse<RevisionDtos.RevisionDetail> legacyDetail(Authentication authentication, @PathVariable UUID revisionId) {
        return ApiResponse.of(queries.legacyDetail(current(authentication), revisionId));
    }

    @Operation(summary = "比较两个修订或修订与当前工作副本", description = "只接受同一记录内的来源；返回结构化领域差异，不比较原始 HTML。")
    @GetMapping("/records/{recordId}/revision-diff")
    ApiResponse<RevisionDtos.DiffResult> diff(Authentication authentication, @PathVariable UUID recordId,
            @RequestParam UUID fromRevisionId, @RequestParam(required = false) UUID toRevisionId,
            @Parameter(description = "与工作副本比较时固定为 WORKING_COPY") @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "false") boolean includeUnchanged) {
        return ApiResponse.of(diffs.compare(current(authentication), recordId, fromRevisionId, toRevisionId, to, includeUnchanged));
    }

    private UUID current(Authentication authentication) { return UUID.fromString(authentication.getName()); }
}
