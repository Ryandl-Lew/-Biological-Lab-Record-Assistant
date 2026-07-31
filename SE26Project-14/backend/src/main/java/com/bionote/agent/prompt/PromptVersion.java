package com.bionote.agent.prompt;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import java.util.UUID;

public record PromptVersion(
        UUID id,
        String name,
        int version,
        String templateText,
        JsonNode outputSchema,
        JsonNode toolPolicy,
        String contentHash,
        Set<String> allowedTools) {}
