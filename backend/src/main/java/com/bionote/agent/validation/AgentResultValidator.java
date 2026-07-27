package com.bionote.agent.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.bionote.agent.runtime.AgentRunContext;

public interface AgentResultValidator { ValidationResult validate(AgentRunContext context,JsonNode candidate,JsonNode schema); }
