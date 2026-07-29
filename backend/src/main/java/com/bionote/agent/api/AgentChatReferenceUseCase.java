package com.bionote.agent.api;

import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface AgentChatReferenceUseCase {
    AgentDtos.ChatReferenceView upload(UUID actor, UUID projectId, MultipartFile file);
    void delete(UUID actor, UUID projectId, UUID referenceId);
}
