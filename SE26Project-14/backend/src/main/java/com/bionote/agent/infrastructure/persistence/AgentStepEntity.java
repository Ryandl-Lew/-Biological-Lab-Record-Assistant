package com.bionote.agent.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "agent_steps")
class AgentStepEntity {
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID id;

    @Column(name = "run_id", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID runId;

    @Column(name = "step_no", nullable = false)
    int stepNo;

    @Column(name = "step_type", nullable = false, length = 30)
    String stepType;

    @Column(name = "tool_name", length = 100)
    String toolName;

    @Column(name = "request_json", columnDefinition = "TEXT")
    String requestJson;

    @Column(name = "response_json", columnDefinition = "TEXT")
    String responseJson;

    @Column(name = "content_hash", nullable = false, length = 64)
    @JdbcTypeCode(SqlTypes.CHAR)
    String contentHash;

    @Column(name = "latency_ms", nullable = false)
    long latencyMs;

    @Column(name = "input_tokens", nullable = false)
    long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    long outputTokens;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    protected AgentStepEntity() {}

    AgentStepEntity(
            UUID id,
            UUID runId,
            int stepNo,
            String stepType,
            String toolName,
            String requestJson,
            String responseJson,
            String contentHash,
            long latencyMs,
            long inputTokens,
            long outputTokens,
            Instant createdAt) {
        this.id = id;
        this.runId = runId;
        this.stepNo = stepNo;
        this.stepType = stepType;
        this.toolName = toolName;
        this.requestJson = requestJson;
        this.responseJson = responseJson;
        this.contentHash = contentHash;
        this.latencyMs = latencyMs;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.createdAt = createdAt;
    }
}
