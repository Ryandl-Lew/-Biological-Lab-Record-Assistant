package com.bionote.agent.api;

import com.bionote.common.PagedResponse;
import java.util.UUID;

public interface AgentRunUseCase {
    AgentDtos.RunView createRecord(
            UUID actor, UUID recordId, AgentDtos.CreateRunRequest request, String idempotencyKey);

    AgentDtos.RunView createProject(
            UUID actor, UUID projectId, AgentDtos.CreateRunRequest request, String idempotencyKey);

    AgentDtos.RunView get(UUID actor, UUID runId);

    PagedResponse<AgentDtos.StepView> steps(UUID actor, UUID runId, int page, int size);

    AgentDtos.RunView cancel(UUID actor, UUID runId);

    AgentDtos.RunView rerun(UUID actor, UUID runId, String idempotencyKey);

    PagedResponse<AgentDtos.ArtifactSummary> recordArtifacts(
            UUID actor, UUID recordId, int page, int size);

    PagedResponse<AgentDtos.ArtifactSummary> projectArtifacts(
            UUID actor, UUID projectId, int page, int size);

    AgentDtos.ArtifactView artifact(UUID actor, UUID artifactId);
}
