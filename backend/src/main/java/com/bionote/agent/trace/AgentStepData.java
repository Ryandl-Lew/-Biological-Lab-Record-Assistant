package com.bionote.agent.trace;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record AgentStepData(UUID id, UUID runId, int stepNo, String stepType, String toolName,
                            JsonNode request, JsonNode response, String contentHash, long latencyMs,
                            long inputTokens, long outputTokens, Instant createdAt) {}
