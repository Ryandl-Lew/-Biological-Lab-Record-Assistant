package com.bionote.agent.api;

import java.util.List;
import java.util.UUID;

public interface AgentChatUseCase {
    AgentDtos.ChatReply chat(UUID actor, UUID recordId, AgentDtos.ChatRequest request);
    AgentDtos.ChatReply chatAboutProject(UUID actor, UUID projectId, AgentDtos.ChatRequest request);
    List<AgentDtos.ChatSessionSummary> listProjectSessions(UUID actor, UUID projectId);
    List<AgentDtos.ChatSessionSummary> listRecordSessions(UUID actor, UUID recordId);
    AgentDtos.ChatSessionDetail getSession(UUID actor, UUID sessionId);
    AgentDtos.ChatSessionDetail saveSession(UUID actor, UUID projectId, UUID recordId, AgentDtos.SaveSessionRequest request);
    AgentDtos.ChatSessionDetail appendSessionMessages(UUID actor, UUID sessionId, List<AgentDtos.ChatMessage> messages);
    void deleteSession(UUID actor, UUID sessionId);
}
