package com.bionote.agent.infrastructure.persistence;

import com.bionote.agent.artifact.AgentArtifactAppender;
import com.bionote.agent.artifact.AgentArtifactReader;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
public class JpaAgentArtifactAdapter implements AgentArtifactAppender, AgentArtifactReader {
    private final AgentArtifactJpaRepository artifacts;

    public JpaAgentArtifactAdapter(AgentArtifactJpaRepository artifacts) {
        this.artifacts = artifacts;
    }

    @Override
    public void append(AgentArtifactAppender.ArtifactRecord value) {
        artifacts.saveAndFlush(
                new AgentArtifactEntity(
                        value.id(),
                        value.runId(),
                        value.artifactKind(),
                        value.projectId(),
                        value.recordId(),
                        value.contentJson(),
                        value.evidenceJson(),
                        value.contentHash(),
                        value.createdAt()));
    }

    @Override
    public Optional<AgentArtifactReader.ArtifactRecord> findById(UUID artifactId) {
        return artifacts.findById(artifactId).map(this::map);
    }

    @Override
    public Optional<AgentArtifactReader.ArtifactRecord> findByRunId(UUID runId) {
        return artifacts.findByRunId(runId).map(this::map);
    }

    @Override
    public PageSlice findByProject(UUID projectId, int page, int size) {
        return new PageSlice(
                artifacts
                        .findByProjectIdOrderByCreatedAtDescIdDesc(
                                projectId, PageRequest.of(page, size))
                        .stream()
                        .map(this::map)
                        .toList(),
                artifacts.countByProjectId(projectId));
    }

    @Override
    public PageSlice findByRecord(UUID recordId, int page, int size) {
        return new PageSlice(
                artifacts
                        .findByRecordIdOrderByCreatedAtDescIdDesc(
                                recordId, PageRequest.of(page, size))
                        .stream()
                        .map(this::map)
                        .toList(),
                artifacts.countByRecordId(recordId));
    }

    private AgentArtifactReader.ArtifactRecord map(AgentArtifactEntity value) {
        return new AgentArtifactReader.ArtifactRecord(
                value.id,
                value.runId,
                value.artifactKind,
                value.projectId,
                value.recordId,
                value.contentJson,
                value.evidenceJson,
                value.contentHash,
                value.createdAt);
    }
}
