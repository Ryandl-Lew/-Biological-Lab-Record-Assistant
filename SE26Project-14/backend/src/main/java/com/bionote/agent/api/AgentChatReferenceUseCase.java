package com.bionote.agent.api;

import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface AgentChatReferenceUseCase {
    AgentDtos.ChatReferenceView upload(UUID actor, UUID projectId, MultipartFile file);

    void delete(UUID actor, UUID projectId, UUID referenceId);
}
