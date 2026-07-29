package com.bionote.agent.model;

import com.bionote.agent.config.AgentCredentialService;
import com.bionote.agent.config.AgentCredentials;
import com.bionote.agent.config.AgentProperties;
import com.bionote.agent.tool.AgentToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_REAL_AGENT_SMOKE", matches = "true")
class RealProviderSmokeTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void normalizesToolCallingAndStructuredJsonWithoutSendingBioNoteData() {
        AgentProperties properties = new AgentProperties();
        properties.setEnabled(true);
        properties.setProvider("openai-compatible");
        AgentCredentials credentials = new AgentCredentialService(properties).resolve(null);
        properties.setBaseUrl(credentials.baseUrl());
        properties.setApiKey(credentials.apiKey());
        properties.setModel(credentials.model());
        properties.setTimeoutMs(45_000);

        OpenAiCompatibleModelClient client = new OpenAiCompatibleModelClient(properties, json);
        ObjectNode inputProperties = json.createObjectNode();
        inputProperties.set("query", json.createObjectNode().put("type", "string"));
        ObjectNode inputSchema = json.createObjectNode();
        inputSchema.put("type", "object");
        inputSchema.set("properties", inputProperties);
        inputSchema.set("required", json.createArrayNode().add("query"));
        AgentToolDefinition tool = new AgentToolDefinition(
                "get_synthetic_demo_fact",
                "Returns one synthetic, non-sensitive fact for an adapter smoke test.",
                inputSchema,
                1_000,
                1,
                Set.of("SMOKE"),
                AgentToolDefinition.SideEffect.READ_ONLY
        );

        UUID runId = UUID.randomUUID();
        UUID promptId = UUID.randomUUID();
        String system = "This is a provider adapter smoke test using synthetic data only. "
                + "First call get_synthetic_demo_fact exactly once. Do not answer from memory.";
        AgentModelRequest firstRequest = new AgentModelRequest(
                runId,
                promptId,
                properties.getModel(),
                system,
                List.of(new AgentModelRequest.ModelMessage(
                        "user",
                        "Use the supplied tool for the synthetic query adapter-smoke.",
                        null,
                        null
                )),
                List.of(tool),
                json.createObjectNode(),
                300
        );

        AgentModelResponse toolResponse = client.complete(firstRequest);
        assertThat(toolResponse.toolCalls()).hasSize(1);
        ModelToolCall call = toolResponse.toolCalls().get(0);
        assertThat(call.name()).isEqualTo("get_synthetic_demo_fact");
        assertThat(call.arguments()).isNotNull();
        assertThat(call.arguments().isObject()).isTrue();

        ObjectNode outputProperties = json.createObjectNode();
        outputProperties.set("schemaVersion", json.createObjectNode().put("type", "integer"));
        ObjectNode statusSchema = json.createObjectNode().put("type", "string");
        statusSchema.set("enum", json.createArrayNode().add("ok"));
        outputProperties.set("status", statusSchema);
        ObjectNode evidenceRefSchema = json.createObjectNode().put("type", "string");
        evidenceRefSchema.set("enum", json.createArrayNode().add("synthetic-1"));
        outputProperties.set("evidenceRef", evidenceRefSchema);
        ObjectNode outputSchema = json.createObjectNode();
        outputSchema.put("type", "object");
        outputSchema.set("properties", outputProperties);
        outputSchema.set("required", json.createArrayNode().add("schemaVersion").add("status").add("evidenceRef"));
        String finalSystem = "Return one JSON object only, with no markdown or explanation. "
                + "It must be exactly compatible with the supplied schema and use the synthetic tool result.";
        AgentModelRequest secondRequest = new AgentModelRequest(
                runId,
                promptId,
                properties.getModel(),
                finalSystem,
                List.of(
                        new AgentModelRequest.ModelMessage(
                                "user",
                                "Produce the final synthetic adapter smoke artifact.",
                                null,
                                null
                        ),
                        new AgentModelRequest.ModelMessage(
                                "assistant",
                                null,
                                null,
                                null,
                                List.of(call)
                        ),
                        new AgentModelRequest.ModelMessage(
                                "tool",
                                "{\"fact\":\"synthetic-only\",\"evidenceRef\":\"synthetic-1\"}",
                                call.id(),
                                call.name()
                        )
                ),
                List.of(tool),
                outputSchema,
                300
        );

        AgentModelResponse finalResponse = client.complete(secondRequest);
        assertThat(finalResponse.toolCalls()).isEmpty();
        assertThat(finalResponse.finalOutput()).isNotNull();
        assertThat(finalResponse.finalOutput().isObject()).isTrue();
        assertThat(finalResponse.finalOutput().path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(finalResponse.finalOutput().path("status").asText()).isEqualTo("ok");
        assertThat(finalResponse.finalOutput().path("evidenceRef").asText()).isEqualTo("synthetic-1");
    }
}
