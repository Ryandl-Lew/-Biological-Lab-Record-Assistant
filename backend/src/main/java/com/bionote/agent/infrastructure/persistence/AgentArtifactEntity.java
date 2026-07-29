package com.bionote.agent.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_artifacts")
class AgentArtifactEntity {
    @Id @JdbcTypeCode(SqlTypes.CHAR) UUID id;
    @Column(name = "run_id", nullable = false) @JdbcTypeCode(SqlTypes.CHAR) UUID runId;
    @Column(name = "artifact_kind", nullable = false, length = 30) String artifactKind;
    @Column(name = "project_id", nullable = false) @JdbcTypeCode(SqlTypes.CHAR) UUID projectId;
    @Column(name = "record_id") @JdbcTypeCode(SqlTypes.CHAR) UUID recordId;
    @Column(name = "content_json", nullable = false, columnDefinition = "TEXT") String contentJson;
    @Column(name = "evidence_json", nullable = false, columnDefinition = "TEXT") String evidenceJson;
    @Column(name = "content_hash", nullable = false, length = 64) @JdbcTypeCode(SqlTypes.CHAR) String contentHash;
    @Column(name = "created_at", nullable = false) Instant createdAt;

    protected AgentArtifactEntity() {}
    AgentArtifactEntity(UUID id, UUID runId, String artifactKind, UUID projectId, UUID recordId,
                        String contentJson, String evidenceJson, String contentHash, Instant createdAt) {
        this.id=id; this.runId=runId; this.artifactKind=artifactKind; this.projectId=projectId;
        this.recordId=recordId; this.contentJson=contentJson; this.evidenceJson=evidenceJson;
        this.contentHash=contentHash; this.createdAt=createdAt;
    }
}
