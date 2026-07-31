package com.bionote.agent.trace;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

/** Append-only persistence port for immutable agent trace steps. */
public interface AgentStepAppender {
    AgentStepData append(
            UUID runId,
            String type,
            String toolName,
            JsonNode request,
            JsonNode response,
            long latencyMs,
            long inputTokens,
            long outputTokens);
}
