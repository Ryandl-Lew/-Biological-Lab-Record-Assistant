package com.bionote.agent.api;

import com.bionote.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@Tag(
        name = "Agent Chat",
        description = "Record/project scoped lightweight Q&A without tools or persistence")
public class AgentChatController {
    private static final Logger log = LoggerFactory.getLogger(AgentChatController.class);
    private final AgentChatUseCase service;
    private final AgentChatReferenceUseCase references;

    public AgentChatController(AgentChatUseCase service, AgentChatReferenceUseCase references) {
        this.service = service;
        this.references = references;
    }

    @Operation(summary = "记录内轻量问答", description = "项目成员可基于当前记录只读上下文提问；不调用领域工具，不落库。")
    @PostMapping("/records/{recordId}/agent-chat")
    ApiResponse<AgentDtos.ChatReply> chatRecord(
            Authentication authentication,
            @PathVariable UUID recordId,
            @Valid @RequestBody AgentDtos.ChatRequest request) {
        log.info(
                "Agent chat request received: type=record recordId={} userId={}",
                recordId,
                authentication.getName());
        return ApiResponse.of(
                service.chat(UUID.fromString(authentication.getName()), recordId, request));
    }

    @Operation(summary = "项目内轻量问答", description = "项目成员可基于当前项目只读上下文提问；不调用领域工具，不落库。")
    @PostMapping("/projects/{projectId}/agent-chat")
    ApiResponse<AgentDtos.ChatReply> chatProject(
            Authentication authentication,
            @PathVariable UUID projectId,
            @Valid @RequestBody AgentDtos.ChatRequest request) {
        log.info(
                "Agent chat request received: type=project projectId={} userId={}",
                projectId,
                authentication.getName());
        return ApiResponse.of(
                service.chatAboutProject(
                        UUID.fromString(authentication.getName()), projectId, request));
    }

    @PostMapping(
            value = "/projects/{projectId}/agent-chat/references",
            consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<AgentDtos.ChatReferenceView> uploadReference(
            Authentication authentication,
            @PathVariable UUID projectId,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.of(
                references.upload(
                        UUID.fromString(authentication.getName()), projectId, file));
    }

    @DeleteMapping("/projects/{projectId}/agent-chat/references/{referenceId}")
    ApiResponse<Void> deleteReference(
            Authentication authentication,
            @PathVariable UUID projectId,
            @PathVariable UUID referenceId) {
        references.delete(
                UUID.fromString(authentication.getName()), projectId, referenceId);
        return ApiResponse.of(null);
    }

    @GetMapping("/projects/{projectId}/agent-chat/sessions")
    ApiResponse<List<AgentDtos.ChatSessionSummary>> listProjectSessions(
            Authentication a, @PathVariable UUID projectId) {
        return ApiResponse.of(service.listProjectSessions(UUID.fromString(a.getName()), projectId));
    }

    @GetMapping("/records/{recordId}/agent-chat/sessions")
    ApiResponse<List<AgentDtos.ChatSessionSummary>> listRecordSessions(
            Authentication a, @PathVariable UUID recordId) {
        return ApiResponse.of(service.listRecordSessions(UUID.fromString(a.getName()), recordId));
    }

    @GetMapping("/agent-chat/sessions/{sessionId}")
    ApiResponse<AgentDtos.ChatSessionDetail> getSession(
            Authentication a, @PathVariable UUID sessionId) {
        return ApiResponse.of(service.getSession(UUID.fromString(a.getName()), sessionId));
    }

    @PostMapping("/projects/{projectId}/agent-chat/sessions")
    ApiResponse<AgentDtos.ChatSessionDetail> saveProjectSession(
            Authentication a,
            @PathVariable UUID projectId,
            @Valid @RequestBody AgentDtos.SaveSessionRequest request) {
        return ApiResponse.of(
                service.saveSession(UUID.fromString(a.getName()), projectId, null, request));
    }

    @PostMapping("/records/{recordId}/agent-chat/sessions")
    ApiResponse<AgentDtos.ChatSessionDetail> saveRecordSession(
            Authentication a,
            @PathVariable UUID recordId,
            @Valid @RequestBody AgentDtos.SaveSessionRequest request) {
        return ApiResponse.of(
                service.saveSession(UUID.fromString(a.getName()), null, recordId, request));
    }

    @DeleteMapping("/agent-chat/sessions/{sessionId}")
    ApiResponse<Void> deleteSession(Authentication a, @PathVariable UUID sessionId) {
        service.deleteSession(UUID.fromString(a.getName()), sessionId);
        return ApiResponse.of(null);
    }

    @PostMapping("/agent-chat/sessions/{sessionId}/messages")
    ApiResponse<AgentDtos.ChatSessionDetail> appendMessages(
            Authentication a,
            @PathVariable UUID sessionId,
            @Valid @RequestBody AgentDtos.SaveSessionRequest request) {
        return ApiResponse.of(
                service.appendSessionMessages(
                        UUID.fromString(a.getName()), sessionId, request.messages()));
    }
}
