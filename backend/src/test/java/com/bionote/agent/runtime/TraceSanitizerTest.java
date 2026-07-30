package com.bionote.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.bionote.agent.trace.TraceSanitizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TraceSanitizerTest {
    @Test
    void redactsSecretsReasoningAndTruncatesLargeText() {
        TraceSanitizer sanitizer = new TraceSanitizer(new ObjectMapper());
        String output =
                sanitizer
                        .sanitize(
                                Map.of(
                                        "Authorization",
                                        "Bearer secret",
                                        "apiKey",
                                        "key",
                                        "chain_of_thought",
                                        "hidden",
                                        "body",
                                        "x".repeat(3000)))
                        .toString();
        assertThat(output)
                .doesNotContain("Bearer secret", "\"key\"", "hidden")
                .contains("[REDACTED]", "[truncated]");
    }
}
