package com.bionote.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;

public record AgentToolDefinition(
        String name,
        String description,
        JsonNode inputSchema,
        int maxOutputChars,
        int maxItems,
        Set<String> artifactKinds,
        SideEffect sideEffect) {
    public AgentToolDefinition {
        artifactKinds = artifactKinds == null ? Set.of() : Set.copyOf(artifactKinds);
    }

    public enum SideEffect {
        READ_ONLY,
        WRITE
    }
}
