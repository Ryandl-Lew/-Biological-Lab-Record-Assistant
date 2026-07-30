package com.bionote.agent.infrastructure.persistence;

import com.bionote.agent.trace.AgentStepAppender;
import com.bionote.agent.trace.AgentStepData;
import com.bionote.agent.trace.AgentStepReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaAgentStepAdapter implements AgentStepAppender, AgentStepReader {
    private final AgentStepJpaRepository steps;
    private final AgentRunJpaRepository runs;
    private final ObjectMapper json;

    public JpaAgentStepAdapter(
            AgentStepJpaRepository steps, AgentRunJpaRepository runs, ObjectMapper json) {
        this.steps = steps;
        this.runs = runs;
        this.json = json;
    }

    @Override
    @Transactional
    public AgentStepData append(
            UUID runId,
            String type,
            String toolName,
            JsonNode request,
            JsonNode response,
            long latencyMs,
            long inputTokens,
            long outputTokens) {
        Integer current = runs.lockStepCount(runId.toString());
        if (current == null) throw new IllegalStateException("Agent run not found");
        int stepNo = current + 1;
        String requestJson = encode(request), responseJson = encode(response);
        String contentHash = hash(type + "|" + toolName + "|" + requestJson + "|" + responseJson);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        long storedLatency = Math.max(0, latencyMs),
                storedInput = Math.max(0, inputTokens),
                storedOutput = Math.max(0, outputTokens);
        steps.saveAndFlush(
                new AgentStepEntity(
                        id,
                        runId,
                        stepNo,
                        type,
                        toolName,
                        requestJson,
                        responseJson,
                        contentHash,
                        storedLatency,
                        storedInput,
                        storedOutput,
                        now));
        runs.updateStepMetrics(runId.toString(), stepNo, storedInput, storedOutput);
        return new AgentStepData(
                id,
                runId,
                stepNo,
                type,
                toolName,
                request,
                response,
                contentHash,
                latencyMs,
                inputTokens,
                outputTokens,
                now);
    }

    @Override
    public List<AgentStepData> list(UUID runId) {
        return steps.findByRunIdOrderByStepNoAsc(runId).stream().map(this::map).toList();
    }

    private AgentStepData map(AgentStepEntity value) {
        return new AgentStepData(
                value.id,
                value.runId,
                value.stepNo,
                value.stepType,
                value.toolName,
                read(value.requestJson),
                read(value.responseJson),
                value.contentHash,
                value.latencyMs,
                value.inputTokens,
                value.outputTokens,
                value.createdAt);
    }

    private String encode(JsonNode value) {
        try {
            return value == null || value.isNull() ? null : json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private JsonNode read(String value) {
        try {
            return value == null ? null : json.readTree(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
