package com.bionote.agent.api;

import java.util.UUID;

public interface AgentChatUseCase {
    AgentDtos.ChatReply chat(UUID actor, UUID recordId, AgentDtos.ChatRequest request);
    AgentDtos.ChatReply chatAboutProject(UUID actor, UUID projectId, AgentDtos.ChatRequest request);
}
