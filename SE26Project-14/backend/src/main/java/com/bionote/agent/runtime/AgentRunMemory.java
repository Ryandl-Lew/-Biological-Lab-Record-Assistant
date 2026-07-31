package com.bionote.agent.runtime;

import com.bionote.agent.model.AgentModelRequest;
import com.bionote.agent.tool.AgentToolExecutor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AgentRunMemory {
    private final Map<String, AgentToolExecutor.ExecutedToolResult> toolCache =
            new LinkedHashMap<>();
    private final List<AgentModelRequest.ModelMessage> messages = new ArrayList<>();
    private final Set<String> evidenceCandidates = new LinkedHashSet<>();
    private final Map<String, Integer> toolCallsByName = new LinkedHashMap<>();
    private int executionSteps;
    private int modelCalls;
    private int repairTurns;

    public AgentToolExecutor.ExecutedToolResult cached(String key) {
        return toolCache.get(key);
    }

    public void cache(String key, AgentToolExecutor.ExecutedToolResult value) {
        toolCache.put(key, value);
    }

    public void addMessage(AgentModelRequest.ModelMessage value) {
        messages.add(value);
    }

    public List<AgentModelRequest.ModelMessage> messages() {
        return List.copyOf(messages);
    }

    public int executionSteps() {
        return executionSteps;
    }

    public void incrementExecutionSteps() {
        executionSteps++;
    }

    public int modelCalls() {
        return modelCalls;
    }

    public void incrementModelCalls() {
        modelCalls++;
    }

    public int repairTurns() {
        return repairTurns;
    }

    public void incrementRepairTurns() {
        repairTurns++;
    }

    public void addEvidenceCandidates(List<String> values) {
        if (values != null) evidenceCandidates.addAll(values);
    }

    public Set<String> evidenceCandidates() {
        return Set.copyOf(evidenceCandidates);
    }

    public int toolCalls(String name) {
        return toolCallsByName.getOrDefault(name, 0);
    }

    public void incrementToolCalls(String name) {
        toolCallsByName.merge(name, 1, Integer::sum);
    }
}
