package com.bionote.agent.artifact;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentArtifactReader {
    Optional<ArtifactRecord> findById(UUID artifactId);
    Optional<ArtifactRecord> findByRunId(UUID runId);
    PageSlice findByProject(UUID projectId, int page, int size);
    PageSlice findByRecord(UUID recordId, int page, int size);

    record ArtifactRecord(UUID id, UUID runId, String artifactKind, UUID projectId, UUID recordId,
                          String contentJson, String evidenceJson, String contentHash, Instant createdAt) {}
    record PageSlice(List<ArtifactRecord> items, long total) {}
}
