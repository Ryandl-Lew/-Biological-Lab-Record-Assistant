package com.bionote.agent.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AgentDtos {
    private AgentDtos() {}

    public record CreateRunRequest(
            @Schema(allowableValues = {"RECORD_SUMMARY", "PROJECT_PROGRESS"}) String artifactKind,
            Instant periodStart,
            Instant periodEnd,
            @Size(max = 500) String focus) {}

    public record RunView(
            UUID id,
            @Schema(
                            allowableValues = {
                                "QUEUED",
                                "RUNNING",
                                "SUCCEEDED",
                                "FAILED",
                                "CANCELLED",
                                "LIMIT_EXCEEDED",
                                "INVALID_OUTPUT"
                            })
                    String status,
            @Schema(allowableValues = {"RECORD_SUMMARY", "PROJECT_PROGRESS"}) String artifactKind,
            @Schema(allowableValues = {"RECORD", "PROJECT"}) String subjectType,
            UUID subjectId,
            UUID projectId,
            UUID recordId,
            UUID requestedBy,
            @Schema(allowableValues = {"MANUAL", "RERUN"}) String triggerType,
            String provider,
            String model,
            UUID promptVersionId,
            UUID parentRunId,
            int stepCount,
            int toolCallCount,
            long inputTokens,
            long outputTokens,
            String errorCode,
            String errorMessage,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt,
            UUID artifactId) {}

    public record StepView(
            UUID id,
            int stepNo,
            String stepType,
            String toolName,
            JsonNode request,
            JsonNode response,
            String contentHash,
            long latencyMs,
            long inputTokens,
            long outputTokens,
            Instant createdAt) {}

    public record ArtifactSummary(
            UUID id,
            UUID runId,
            String artifactKind,
            UUID projectId,
            UUID recordId,
            String headline,
            JsonNode period,
            String contentHash,
            Instant createdAt) {}

    public record ArtifactView(
            UUID id,
            UUID runId,
            String artifactKind,
            UUID projectId,
            UUID recordId,
            JsonNode content,
            JsonNode evidence,
            String contentHash,
            Instant createdAt) {}

    public record ChatMessage(
            @Schema(allowableValues = {"user", "assistant"}) String role,
            @Size(max = 12000) String content,
            String metadata) {
        public ChatMessage(String role, String content) {
            this(role, content, null);
        }
    }

    public record FitProposalView(
            String equation,
            boolean autoCompare,
            List<String> candidateIds,
            String xSpec,
            String ySpec,
            String pointSource,
            String csvNameHint,
            List<String> recordCodes,
            List<String> statuses,
            String experimentType,
            String keyword,
            String rationale,
            Boolean multivariate,
            Boolean timeToMinutes) {}

    public record ChatRequest(
            @NotBlank @Size(max = 2000) String message,
            @Size(max = 20) @Valid List<ChatMessage> history,
            FitProposalView fitConfirm,
            @Size(max = 5) List<UUID> referenceIds) {
        public ChatRequest(
                String message, List<ChatMessage> history, FitProposalView fitConfirm) {
            this(message, history, fitConfirm, List.of());
        }
    }

    public record ChatReferenceView(
            UUID id,
            String filename,
            String contentType,
            long sizeBytes,
            String kind,
            List<String> columns,
            String textPreview,
            String note,
            Instant expiresAt) {}

    public record FitPointView(double x, double y, String recordCode, String source) {}

    public record FitCurvePoint(double x, double y) {}

    public record FitSkipView(String recordCode, String reason) {}

    public record FitComparisonView(
            String equation, Double rSquared, Double rmse, int n, boolean selected) {}

    public record SuggestedEvidenceFitView(
            String purpose, String xHint, String yHint, String note) {}

    public record AnalysisTemplateView(
            String id,
            String label,
            String description,
            List<String> outputSections,
            List<String> uncertaintyChecklist,
            List<SuggestedEvidenceFitView> suggestedFits) {}

    public record FitView(
            String equation,
            Map<String, Double> parameters,
            double rSquared,
            double rmse,
            int n,
            List<String> usedRecordCodes,
            List<FitSkipView> skipped,
            List<FitCurvePoint> curveSample,
            List<FitPointView> points,
            List<FitComparisonView> comparisons) {}

    public record ChartPointView(double x, double y, String label) {}

    public record ChartSeriesView(String name, List<ChartPointView> points) {}

    public record ChartView(
            String type,
            String title,
            String xLabel,
            String yLabel,
            List<ChartSeriesView> series) {}

    public record ChatReply(
            String reply,
            String provider,
            String model,
            FitView fit,
            List<FitView> fits,
            FitProposalView proposal,
            AnalysisTemplateView analysisTemplate,
            ChartView chart,
            String systemError) {}

    public record ChatSessionSummary(
            UUID id, String title, Instant createdAt, Instant updatedAt, int messageCount) {}

    public record ChatSessionDetail(
            UUID id,
            UUID projectId,
            UUID recordId,
            String title,
            List<ChatMessage> messages,
            Instant createdAt,
            Instant updatedAt) {}

    public record SaveSessionRequest(String title, List<ChatMessage> messages) {}
}
