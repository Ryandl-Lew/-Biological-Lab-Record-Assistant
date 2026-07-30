package com.bionote.agent.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "agent_runs")
class AgentRunEntity {
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID id;

    @Column(name = "artifact_kind", nullable = false, length = 30)
    String artifactKind;

    @Column(name = "subject_type", nullable = false, length = 20)
    String subjectType;

    @Column(name = "subject_id", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID subjectId;

    @Column(name = "project_id", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID projectId;

    @Column(name = "record_id")
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID recordId;

    @Column(name = "requested_by", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID requestedBy;

    @Column(name = "trigger_type", nullable = false, length = 20)
    String triggerType;

    @Column(nullable = false, length = 30)
    String status;

    @Column(nullable = false, length = 60)
    String provider;

    @Column(nullable = false, length = 120)
    String model;

    @Column(name = "prompt_version_id", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID promptVersionId;

    @Column(name = "parent_run_id")
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID parentRunId;

    @Column(name = "idempotency_key", nullable = false, length = 160)
    String idempotencyKey;

    @Column(name = "request_json", nullable = false, columnDefinition = "TEXT")
    String requestJson;

    @Column(name = "payload_hash", nullable = false, length = 64)
    @JdbcTypeCode(SqlTypes.CHAR)
    String payloadHash;

    @Column(name = "input_cursor_json", nullable = false, columnDefinition = "TEXT")
    String inputCursorJson;

    @Column(name = "limits_json", nullable = false, columnDefinition = "TEXT")
    String limitsJson;

    @Column(name = "step_count", nullable = false)
    int stepCount;

    @Column(name = "tool_call_count", nullable = false)
    int toolCallCount;

    @Column(name = "input_tokens", nullable = false)
    long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    long outputTokens;

    @Column(name = "error_code", length = 80)
    String errorCode;

    @Column(name = "error_message", length = 1000)
    String errorMessage;

    @Column(name = "cancel_requested_at")
    Instant cancelRequestedAt;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "started_at")
    Instant startedAt;

    @Column(name = "finished_at")
    Instant finishedAt;

    @Version long version;

    protected AgentRunEntity() {}

    AgentRunEntity(
            UUID id,
            String artifactKind,
            String subjectType,
            UUID subjectId,
            UUID projectId,
            UUID recordId,
            UUID requestedBy,
            String triggerType,
            String provider,
            String model,
            UUID promptVersionId,
            UUID parentRunId,
            String idempotencyKey,
            String requestJson,
            String payloadHash,
            String inputCursorJson,
            String limitsJson,
            Instant createdAt) {
        this.id = id;
        this.artifactKind = artifactKind;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.projectId = projectId;
        this.recordId = recordId;
        this.requestedBy = requestedBy;
        this.triggerType = triggerType;
        this.status = "QUEUED";
        this.provider = provider;
        this.model = model;
        this.promptVersionId = promptVersionId;
        this.parentRunId = parentRunId;
        this.idempotencyKey = idempotencyKey;
        this.requestJson = requestJson;
        this.payloadHash = payloadHash;
        this.inputCursorJson = inputCursorJson;
        this.limitsJson = limitsJson;
        this.createdAt = createdAt;
    }
}
