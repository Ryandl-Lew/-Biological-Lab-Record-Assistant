package com.bionote.agent.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AgentToolRegistry {
    private final Map<String, AgentTool<?, ?>> tools;

    public AgentToolRegistry(List<AgentTool<?, ?>> discovered) {
        Map<String, AgentTool<?, ?>> values = new LinkedHashMap<>();
        for (AgentTool<?, ?> tool : discovered) {
            AgentToolDefinition d = tool.definition();
            if (d == null || d.name() == null || !d.name().matches("[a-z][a-z0-9_]{1,99}"))
                throw new IllegalStateException("Invalid agent tool name");
            if (d.description() == null || d.description().isBlank())
                throw new IllegalStateException("Agent tool description is required: " + d.name());
            if (d.inputSchema() == null
                    || !d.inputSchema().isObject()
                    || !"object".equals(d.inputSchema().path("type").asText()))
                throw new IllegalStateException(
                        "Agent tool schema must be a JSON object schema: " + d.name());
            if (d.maxOutputChars() < 1 || d.maxItems() < 1)
                throw new IllegalStateException(
                        "Agent tool output limits must be positive: " + d.name());
            if (d.sideEffect() != AgentToolDefinition.SideEffect.READ_ONLY)
                throw new IllegalStateException("Only READ_ONLY tools are allowed: " + d.name());
            if (values.putIfAbsent(d.name(), tool) != null)
                throw new IllegalStateException("Duplicate agent tool: " + d.name());
        }
        tools = Map.copyOf(values);
    }

    public Optional<AgentTool<?, ?>> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public List<AgentToolDefinition> definitions() {
        return tools.values().stream().map(AgentTool::definition).toList();
    }
}
