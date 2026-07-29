package com.bionote.agent.artifact;

import java.time.Instant;
import java.util.UUID;

/** Append-only persistence port for immutable agent artifacts. */
public interface AgentArtifactAppender {
    void append(ArtifactRecord artifact);

    record ArtifactRecord(UUID id, UUID runId, String artifactKind, UUID projectId, UUID recordId,
                          String contentJson, String evidenceJson, String contentHash, Instant createdAt) {}
}
