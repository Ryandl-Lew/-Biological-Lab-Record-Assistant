package com.bionote.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bionote.agent.tool.AgentTool;
import com.bionote.agent.tool.AgentToolContext;
import com.bionote.agent.tool.AgentToolDefinition;
import com.bionote.agent.tool.AgentToolRegistry;
import com.bionote.agent.tool.AgentToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AgentToolRegistryTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void registersMultipleReadOnlyTools() {
        AgentToolRegistry registry =
                new AgentToolRegistry(
                        List.of(
                                tool("one", AgentToolDefinition.SideEffect.READ_ONLY, schema()),
                                tool("two", AgentToolDefinition.SideEffect.READ_ONLY, schema())));
        assertThat(registry.definitions())
                .extracting(AgentToolDefinition::name)
                .containsExactlyInAnyOrder("one", "two");
    }

    @Test
    void rejectsDuplicateNames() {
        assertThatThrownBy(
                        () ->
                                new AgentToolRegistry(
                                        List.of(
                                                tool(
                                                        "same",
                                                        AgentToolDefinition.SideEffect.READ_ONLY,
                                                        schema()),
                                                tool(
                                                        "same",
                                                        AgentToolDefinition.SideEffect.READ_ONLY,
                                                        schema()))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void rejectsWriteToolsAndInvalidSchemas() {
        assertThatThrownBy(
                        () ->
                                new AgentToolRegistry(
                                        List.of(
                                                tool(
                                                        "write_tool",
                                                        AgentToolDefinition.SideEffect.WRITE,
                                                        schema()))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("READ_ONLY");
        assertThatThrownBy(
                        () ->
                                new AgentToolRegistry(
                                        List.of(
                                                tool(
                                                        "bad_schema",
                                                        AgentToolDefinition.SideEffect.READ_ONLY,
                                                        json.createArrayNode()))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("schema");
    }

    private com.fasterxml.jackson.databind.JsonNode schema() {
        return json.createObjectNode().put("type", "object");
    }

    private AgentTool<Map, Map> tool(
            String name,
            AgentToolDefinition.SideEffect sideEffect,
            com.fasterxml.jackson.databind.JsonNode schema) {
        return new AgentTool<>() {
            public AgentToolDefinition definition() {
                return new AgentToolDefinition(
                        name, "test tool", schema, 1000, 10, Set.of(), sideEffect);
            }

            public Class<Map> inputType() {
                return Map.class;
            }

            public AgentToolResult<Map> execute(AgentToolContext context, Map input) {
                return AgentToolResult.of(input, "ok");
            }
        };
    }
}
