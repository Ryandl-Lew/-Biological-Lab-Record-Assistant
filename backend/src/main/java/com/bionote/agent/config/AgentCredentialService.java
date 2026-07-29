package com.bionote.agent.config;

import com.bionote.common.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves the single shared LLM configuration from the repository-local {@code llm} file.
 * Values are read on demand so updating the file does not require persisting credentials elsewhere.
 */
@Service
public class AgentCredentialService {
    private final AgentProperties properties;
    private final String configuredPath;

    @Autowired
    public AgentCredentialService(AgentProperties properties,
                                  @Value("${bionote.llm-config-path:}") String configuredPath) {
        this.properties = properties;
        this.configuredPath = configuredPath == null ? "" : configuredPath.trim();
    }

    public AgentCredentialService(AgentProperties properties) {
        this(properties, "");
    }

    public AgentCredentials resolve(UUID userId) {
        if ("fake".equalsIgnoreCase(properties.getProvider())) {
            return new AgentCredentials(
                    "fake",
                    blank(properties.getModel()) ? "fake-deterministic-v1" : properties.getModel(),
                    "",
                    ""
            );
        }
        return readLlmFile(resolvePath());
    }

    AgentCredentials readLlmFile(Path path) {
        Map<String, String> values = new LinkedHashMap<>();
        try {
            for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int colon = line.indexOf(':');
                int equals = line.indexOf('=');
                int separator = colon < 0 ? equals : equals < 0 ? colon : Math.min(colon, equals);
                if (separator <= 0) continue;
                String key = normalizeKey(line.substring(0, separator));
                String value = stripQuotes(line.substring(separator + 1).trim());
                values.put(key, value);
            }
        } catch (IOException e) {
            throw unavailable("无法读取 llm 配置文件");
        }

        String baseUrl = values.get("base_url(openai)");
        String apiKey = values.get("api_key");
        String model = values.get("model");
        if (blank(baseUrl) || blank(apiKey) || blank(model)) {
            throw unavailable("llm 配置文件必须包含 base_url (OpenAI)、api_key 和 model");
        }
        if (!baseUrl.endsWith("/")) baseUrl += "/";
        return new AgentCredentials(
                "openai-compatible",
                model,
                baseUrl,
                apiKey
        );
    }

    private Path resolvePath() {
        List<Path> candidates = blank(configuredPath)
                ? List.of(Path.of("llm"), Path.of("..", "llm"))
                : List.of(Path.of(configuredPath));
        return candidates.stream().map(Path::toAbsolutePath).map(Path::normalize)
                .filter(Files::isRegularFile).findFirst()
                .orElseThrow(() -> unavailable("未找到 llm 配置文件"));
    }

    private String normalizeKey(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "").replace("*", "");
    }

    private String stripQuotes(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    private ApiException unavailable(String message) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MODEL_PROVIDER_UNAVAILABLE", message);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
