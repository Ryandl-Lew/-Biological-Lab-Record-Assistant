package com.bionote.agent.model;

import com.bionote.agent.config.AgentCredentialService;
import com.bionote.agent.config.AgentCredentials;
import com.bionote.agent.config.AgentProperties;
import com.bionote.agent.tool.AgentToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(name = "agent.provider", havingValue = "openai-compatible")
public class OpenAiCompatibleModelClient implements AgentModelClient {
    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleModelClient.class);
    private final AgentProperties properties;
    private final ObjectMapper json;
    private final AgentCredentialService credentials;

    @Autowired
    public OpenAiCompatibleModelClient(
            AgentProperties properties, ObjectMapper json, AgentCredentialService credentials) {
        this.properties = properties;
        this.json = json;
        this.credentials = credentials;
    }

    public OpenAiCompatibleModelClient(AgentProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
        this.credentials = null;
        if (properties.isEnabled()
                && (blank(properties.getBaseUrl())
                        || blank(properties.getApiKey())
                        || blank(properties.getModel())))
            throw new IllegalStateException(
                    "Base URL, API key and model are required when the real agent provider is enabled");
    }

    @Override
    public String provider() {
        return "openai-compatible";
    }

    @Override
    public ModelCapabilities capabilities() {
        return new ModelCapabilities(true, true);
    }

    @Override
    public AgentModelResponse complete(AgentModelRequest request) {
        ModelClientException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return completeOnce(request);
            } catch (ModelClientException e) {
                last = e;
                if (attempt == 3 || !retryable(e)) throw e;
                try {
                    Thread.sleep(attempt == 1 ? 300 : 900);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw last;
    }

    private AgentModelResponse completeOnce(AgentModelRequest request) {
        AgentCredentials active = activeCredentials();
        String model =
                request != null && !blank(request.model()) ? request.model() : active.model();
        if (blank(active.baseUrl()) || blank(active.apiKey()) || blank(model))
            throw new ModelClientException(
                    "MODEL_PROVIDER_UNAVAILABLE", "llm configuration is incomplete");
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", messages(request));
            if (!request.tools().isEmpty()) {
                body.put("tools", request.tools().stream().map(this::tool).toList());
                body.put("tool_choice", "auto");
            } else {
                body.put("response_format", Map.of("type", "json_object"));
            }
            body.put("temperature", 0);
            body.put("max_tokens", request.maxOutputTokens());
            if (model.toLowerCase().startsWith("deepseek"))
                body.put("thinking", Map.of("type", "disabled"));
            log.debug(
                    "LLM request: model={} messages={} tools={}",
                    model,
                    messages(request).size(),
                    request.tools().size());
            if (log.isTraceEnabled()) {
                try {
                    log.trace("LLM request body: {}", json.writeValueAsString(body));
                } catch (Exception ignored) {
                }
            }
            JsonNode root =
                    client(active.baseUrl())
                            .post()
                            .uri("chat/completions")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + active.apiKey())
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .body(JsonNode.class);
            if (log.isDebugEnabled()) {
                JsonNode usage = root.path("usage");
                log.debug(
                        "LLM response: prompt_tokens={} completion_tokens={}",
                        usage.path("prompt_tokens").asLong(0),
                        usage.path("completion_tokens").asLong(0));
            }
            return normalize(root);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String code =
                    status == 429
                            ? "AGENT_RATE_LIMITED"
                            : status == 401 || status == 403
                                    ? "MODEL_PROVIDER_UNAVAILABLE"
                                    : status >= 500
                                            ? "MODEL_PROVIDER_UNAVAILABLE"
                                            : "MODEL_PROVIDER_ERROR";
            throw new ModelClientException(
                    code, "Model provider request failed with status " + status);
        } catch (ResourceAccessException e) {
            throw new ModelClientException("AGENT_TIMEOUT", "Model provider timed out", e);
        } catch (ModelClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelClientException(
                    "MODEL_PROVIDER_ERROR",
                    "Model provider response could not be processed: " + e.getMessage(),
                    e);
        }
    }

    private boolean retryable(ModelClientException error) {
        if ("AGENT_TIMEOUT".equals(error.code()) || "AGENT_RATE_LIMITED".equals(error.code()))
            return true;
        String message = error.getMessage() == null ? "" : error.getMessage();
        return "MODEL_PROVIDER_UNAVAILABLE".equals(error.code())
                && (message.contains("status 5") || error.getCause() instanceof ResourceAccessException);
    }

    private AgentCredentials activeCredentials() {
        if (credentials != null) {
            try {
                return credentials.resolve(null);
            } catch (com.bionote.common.ApiException e) {
                throw new ModelClientException("MODEL_PROVIDER_UNAVAILABLE", e.getMessage(), e);
            }
        }
        return new AgentCredentials(
                properties.getProvider(),
                properties.getModel(),
                properties.getBaseUrl(),
                properties.getApiKey());
    }

    private RestClient client(String baseUrl) {
        String base = baseUrl.replaceAll("/*$", "/");
        var factory =
                new org.springframework.http.client.JdkClientHttpRequestFactory(
                        java.net.http.HttpClient.newBuilder()
                                .connectTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                                .build());
        factory.setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        return RestClient.builder().baseUrl(base).requestFactory(factory).build();
    }

    List<Map<String, Object>> messages(AgentModelRequest request) {
        List<Map<String, Object>> values = new ArrayList<>();
        String system = request.systemPrompt();
        if (request.outputSchema() != null
                && !request.outputSchema().isNull()
                && !request.outputSchema().isMissingNode()
                && !request.outputSchema().isEmpty())
            system =
                    system
                            + "\n\nOUTPUT_JSON_SCHEMA (return one JSON object only):\n"
                            + request.outputSchema();
        values.add(Map.of("role", "system", "content", system));
        for (var message : request.messages()) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("role", message.role());
            if (message.content() != null) value.put("content", message.content());
            else if (message.toolCalls().isEmpty()) value.put("content", "");
            if (message.toolCallId() != null) value.put("tool_call_id", message.toolCallId());
            if (message.toolName() != null) value.put("name", message.toolName());
            if (!message.toolCalls().isEmpty())
                value.put(
                        "tool_calls",
                        message.toolCalls().stream()
                                .map(
                                        call ->
                                                Map.of(
                                                        "id",
                                                        call.id(),
                                                        "type",
                                                        "function",
                                                        "function",
                                                        Map.of(
                                                                "name",
                                                                call.name(),
                                                                "arguments",
                                                                call.arguments() == null
                                                                        ? "{}"
                                                                        : call.arguments()
                                                                                .toString())))
                                .toList());
            values.add(value);
        }
        return values;
    }

    private Map<String, Object> tool(AgentToolDefinition definition) {
        return Map.of(
                "type",
                "function",
                "function",
                Map.of(
                        "name",
                        definition.name(),
                        "description",
                        definition.description(),
                        "parameters",
                        definition.inputSchema()));
    }

    private AgentModelResponse normalize(JsonNode root) throws Exception {
        JsonNode choice = root.path("choices").path(0).path("message");
        List<ModelToolCall> calls = new ArrayList<>();
        for (JsonNode call : choice.path("tool_calls")) {
            JsonNode function = call.path("function");
            JsonNode arguments = parseToolArguments(function.path("arguments"));
            calls.add(
                    new ModelToolCall(
                            call.path("id").asText(), function.path("name").asText(), arguments));
        }
        JsonNode output = null;
        if (calls.isEmpty()) {
            String content = contentText(choice.path("content"));
            if (content != null && !content.isBlank()) {
                try {
                    output = parseJsonContent(content);
                } catch (Exception e) {
                    return AgentModelResponse.error(
                            "AGENT_INVALID_OUTPUT",
                            "Final model content was not valid JSON: "
                                    + limit(e.getMessage(), 180));
                }
            } else if ("length"
                    .equals(root.path("choices").path(0).path("finish_reason").asText())) {
                return AgentModelResponse.error(
                        "AGENT_INVALID_OUTPUT",
                        "Model output was truncated before a complete JSON object was returned");
            }
        }
        JsonNode usage = root.path("usage");
        return new AgentModelResponse(
                calls,
                output,
                usage.path("prompt_tokens").asLong(0),
                usage.path("completion_tokens").asLong(0),
                null,
                null);
    }

    JsonNode parseJsonContent(String content) throws Exception {
        String value = content == null ? "" : content.trim();
        if (value.startsWith("```")) {
            int newline = value.indexOf('\n');
            int fence = value.lastIndexOf("```");
            if (newline > 2 && fence > newline) {
                String language = value.substring(3, newline).trim();
                if (language.isEmpty() || "json".equalsIgnoreCase(language))
                    value = value.substring(newline + 1, fence).trim();
            }
        }
        try {
            return json.readTree(value);
        } catch (Exception first) {
            int start = value.indexOf('{');
            int end = value.lastIndexOf('}');
            if (start >= 0 && end > start) return json.readTree(value.substring(start, end + 1));
            throw first;
        }
    }

    private JsonNode parseToolArguments(JsonNode arguments) throws Exception {
        if (arguments == null || arguments.isNull() || arguments.isMissingNode())
            return json.createObjectNode();
        if (arguments.isObject() || arguments.isArray()) return arguments;
        if (arguments.isTextual()) {
            String text = arguments.asText();
            if (text == null || text.isBlank()) return json.createObjectNode();
            return json.readTree(text);
        }
        return json.createObjectNode();
    }

    private String contentText(JsonNode content) {
        if (content == null || content.isNull() || content.isMissingNode()) return null;
        if (content.isTextual()) return content.asText();
        if (content.isArray()) {
            StringBuilder text = new StringBuilder();
            for (JsonNode part : content) {
                if (part.isTextual()) text.append(part.asText());
                else if (part.hasNonNull("text")) text.append(part.path("text").asText());
            }
            return text.toString();
        }
        return content.toString();
    }

    private String limit(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
