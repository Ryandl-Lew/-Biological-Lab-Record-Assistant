package com.bionote.agent.model;

import com.bionote.agent.tool.AgentToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;

public record AgentModelRequest(
        UUID runId,
        UUID promptVersionId,
        String model,
        String systemPrompt,
        List<ModelMessage> messages,
        List<AgentToolDefinition> tools,
        JsonNode outputSchema,
        int maxOutputTokens) {
    public record ModelMessage(
            String role,
            String content,
            String toolCallId,
            String toolName,
            List<ModelToolCall> toolCalls) {
        public ModelMessage {
            toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        }

        public ModelMessage(String role, String content, String toolCallId, String toolName) {
            this(role, content, toolCallId, toolName, List.of());
        }
    }
}
