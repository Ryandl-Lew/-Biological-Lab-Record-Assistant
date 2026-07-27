package com.bionote.agent.runtime;

import com.bionote.agent.model.ModelToolCall;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public sealed interface PlannerDecision permits PlannerDecision.ExecuteTools,PlannerDecision.Finish,PlannerDecision.Fail {
    record ExecuteTools(List<ModelToolCall> invocations) implements PlannerDecision { public ExecuteTools{invocations=List.copyOf(invocations);} }
    record Finish(JsonNode candidateArtifact) implements PlannerDecision {}
    record Fail(String code,String message) implements PlannerDecision {}
}
