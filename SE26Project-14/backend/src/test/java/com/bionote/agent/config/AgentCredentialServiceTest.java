package com.bionote.agent.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentCredentialServiceTest {
    @TempDir Path tempDir;

    @Test
    void readsOpenAiCompatibleCredentialsFromLlmFile() throws Exception {
        Path llm = tempDir.resolve("llm");
        Files.writeString(
                llm,
                """
                base_url (OpenAI): https://llm.example.test/v1
                base_url (Anthropic): https://llm.example.test/anthropic

                api_key=synthetic-test-key
                model*=synthetic-model
                """,
                StandardCharsets.UTF_8);
        AgentProperties properties = new AgentProperties();
        properties.setProvider("openai-compatible");

        AgentCredentials credentials =
                new AgentCredentialService(properties, llm.toString()).resolve(null);

        assertThat(credentials.provider()).isEqualTo("openai-compatible");
        assertThat(credentials.baseUrl()).isEqualTo("https://llm.example.test/v1/");
        assertThat(credentials.model()).isEqualTo("synthetic-model");
        assertThat(credentials.apiKey()).isEqualTo("synthetic-test-key");
    }

    @Test
    void fakeProviderDoesNotReadLlmFile() {
        AgentProperties properties = new AgentProperties();
        properties.setProvider("fake");
        properties.setModel("fake-deterministic-v1");

        AgentCredentials credentials =
                new AgentCredentialService(properties, tempDir.resolve("missing-llm").toString())
                        .resolve(null);

        assertThat(credentials.provider()).isEqualTo("fake");
        assertThat(credentials.model()).isEqualTo("fake-deterministic-v1");
    }
}
