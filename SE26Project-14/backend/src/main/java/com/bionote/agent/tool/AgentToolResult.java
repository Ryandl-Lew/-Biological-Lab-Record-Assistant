package com.bionote.agent.tool;

import java.util.List;

public record AgentToolResult<O>(O data, List<String> evidenceCandidates, String summary) {
    public AgentToolResult {
        evidenceCandidates =
                evidenceCandidates == null ? List.of() : List.copyOf(evidenceCandidates);
    }

    public static <O> AgentToolResult<O> of(O data, String summary) {
        return new AgentToolResult<>(data, List.of(), summary);
    }
}
