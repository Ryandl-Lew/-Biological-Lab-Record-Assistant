package com.bionote.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bionote.agent.prompt.PromptVersionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PromptVersionServiceTest {
    @Autowired PromptVersionService prompts;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM prompt_versions WHERE prompt_name='unit-prompt'");
    }

    @Test
    void registrationIsIdempotentRejectsMutationAndAllowsV2() {
        var v1 =
                prompts.register(
                        "unit-prompt",
                        1,
                        "system\n",
                        "{\"type\":\"object\"}",
                        "{\"allowedTools\":[]}",
                        "a".repeat(64),
                        true);
        assertThat(
                        prompts.register(
                                        "unit-prompt",
                                        1,
                                        "system\n",
                                        "{\"type\":\"object\"}",
                                        "{\"allowedTools\":[]}",
                                        "a".repeat(64),
                                        true)
                                .id())
                .isEqualTo(v1.id());
        assertThatThrownBy(
                        () ->
                                prompts.register(
                                        "unit-prompt",
                                        1,
                                        "changed",
                                        "{\"type\":\"object\"}",
                                        "{\"allowedTools\":[]}",
                                        "b".repeat(64),
                                        true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("new version");
        var v2 =
                prompts.register(
                        "unit-prompt",
                        2,
                        "v2\n",
                        "{\"type\":\"object\"}",
                        "{\"allowedTools\":[]}",
                        "c".repeat(64),
                        true);
        assertThat(prompts.active("unit-prompt").id()).isEqualTo(v2.id());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM prompt_versions WHERE prompt_name='unit-prompt'",
                                Integer.class))
                .isEqualTo(2);
    }

    @Test
    void catalogActivatesV2WithoutMutatingPublishedV1() {
        assertThat(prompts.active("record-summary").version()).isEqualTo(2);
        assertThat(prompts.active("project-progress").version()).isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM prompt_versions WHERE prompt_name IN ('record-summary','project-progress') AND version_no=1",
                                Integer.class))
                .isEqualTo(2);
    }
}
