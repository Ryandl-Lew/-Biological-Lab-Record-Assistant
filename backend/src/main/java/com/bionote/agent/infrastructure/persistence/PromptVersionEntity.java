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
@Table(name = "prompt_versions")
class PromptVersionEntity {
    @Id @JdbcTypeCode(SqlTypes.CHAR) UUID id;
    @Column(name = "prompt_name", nullable = false, length = 100) String promptName;
    @Column(name = "version_no", nullable = false) int versionNo;
    @Column(name = "template_text", nullable = false, columnDefinition = "TEXT") String templateText;
    @Column(name = "output_schema_json", nullable = false, columnDefinition = "TEXT") String outputSchemaJson;
    @Column(name = "tool_policy_json", nullable = false, columnDefinition = "TEXT") String toolPolicyJson;
    @Column(name = "content_hash", nullable = false, length = 64) @JdbcTypeCode(SqlTypes.CHAR) String contentHash;
    @Column(nullable = false) boolean active;
    @Column(name = "active_name_key", length = 100) String activeNameKey;
    @Column(name = "created_at", nullable = false) Instant createdAt;

    protected PromptVersionEntity() {}
    PromptVersionEntity(UUID id, String promptName, int versionNo, String templateText, String outputSchemaJson,
                        String toolPolicyJson, String contentHash, boolean active, String activeNameKey,
                        Instant createdAt) {
        this.id=id; this.promptName=promptName; this.versionNo=versionNo; this.templateText=templateText;
        this.outputSchemaJson=outputSchemaJson; this.toolPolicyJson=toolPolicyJson; this.contentHash=contentHash;
        this.active=active; this.activeNameKey=activeNameKey; this.createdAt=createdAt;
    }
}
