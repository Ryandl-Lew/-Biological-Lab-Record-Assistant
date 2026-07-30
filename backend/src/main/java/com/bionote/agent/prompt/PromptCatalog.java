package com.bionote.agent.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

@Component
public class PromptCatalog {
    private static final Pattern PATH =
            Pattern.compile("/prompts/([^/]+)/v(\\d+)/system\\.md(?:!.*)?$");
    private final PromptVersionService versions;
    private final ObjectMapper json;

    public PromptCatalog(PromptVersionService versions, ObjectMapper json) {
        this.versions = versions;
        this.json = json;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(0)
    public void registerAll() {
        try {
            Resource[] resources =
                    new PathMatchingResourcePatternResolver()
                            .getResources("classpath*:prompts/*/v*/system.md");
            Map<String, PromptSource> discovered = new LinkedHashMap<>();
            for (Resource resource : resources) {
                String url = resource.getURL().toString().replace('\\', '/');
                Matcher matcher = PATH.matcher(url);
                if (!matcher.find()) continue;
                String name = matcher.group(1);
                int version = Integer.parseInt(matcher.group(2));
                String base = "classpath:prompts/" + name + "/v" + version + "/";
                String system = normalize(resource.getContentAsString(StandardCharsets.UTF_8));
                String schema = canonical(read(base + "output-schema.json"));
                String policy = canonical(read(base + "tool-policy.json"));
                String hash =
                        sha256(system + "\n---schema---\n" + schema + "\n---policy---\n" + policy);
                discovered.put(
                        name + ":" + version,
                        new PromptSource(name, version, system, schema, policy, hash));
            }
            Map<String, Integer> latest = new LinkedHashMap<>();
            discovered
                    .values()
                    .forEach(value -> latest.merge(value.name(), value.version(), Math::max));
            discovered.values().stream()
                    .sorted(
                            Comparator.comparing(PromptSource::name)
                                    .thenComparingInt(PromptSource::version))
                    .forEach(
                            value ->
                                    versions.register(
                                            value.name(),
                                            value.version(),
                                            value.system(),
                                            value.schema(),
                                            value.policy(),
                                            value.hash(),
                                            latest.get(value.name()) == value.version()));
        } catch (Exception e) {
            throw e instanceof RuntimeException runtime
                    ? runtime
                    : new IllegalStateException("Prompt catalog registration failed", e);
        }
    }

    private String read(String path) throws Exception {
        return new PathMatchingResourcePatternResolver()
                .getResource(path)
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private String canonical(String value) throws Exception {
        return json.writeValueAsString(json.readTree(value));
    }

    private String normalize(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n').trim() + "\n";
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of()
                .formatHex(
                        MessageDigest.getInstance("SHA-256")
                                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private record PromptSource(
            String name, int version, String system, String schema, String policy, String hash) {}
}
