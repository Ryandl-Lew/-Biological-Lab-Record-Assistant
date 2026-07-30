package com.bionote.agent.validation;

import com.bionote.agent.runtime.AgentRunContext;
import com.fasterxml.jackson.databind.JsonNode;

public interface AgentResultValidator {
    ValidationResult validate(AgentRunContext context, JsonNode candidate, JsonNode schema);
}
