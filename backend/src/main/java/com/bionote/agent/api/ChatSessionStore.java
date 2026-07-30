package com.bionote.agent.api;

import java.util.List;
import java.util.UUID;

public interface ChatSessionStore {
    List<AgentDtos.ChatSessionSummary> listByProject(UUID userId, UUID projectId);
    List<AgentDtos.ChatSessionSummary> listByRecord(UUID userId, UUID recordId);
    AgentDtos.ChatSessionDetail get(UUID userId, UUID sessionId);
    AgentDtos.ChatSessionDetail save(UUID userId, UUID projectId, UUID recordId, AgentDtos.SaveSessionRequest request);
    AgentDtos.ChatSessionDetail appendMessages(UUID userId, UUID sessionId, List<AgentDtos.ChatMessage> messages);
    void delete(UUID userId, UUID sessionId);
}
