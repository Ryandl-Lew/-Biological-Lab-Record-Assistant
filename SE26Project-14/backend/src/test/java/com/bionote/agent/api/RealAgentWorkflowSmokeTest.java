package com.bionote.agent.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.bionote.agent.runtime.AgentRunWorker;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        properties = {
            "agent.enabled=true",
            "agent.provider=openai-compatible",
            "agent.worker-poll-ms=60000",
            "agent.timeout-ms=45000",
            "agent.max-duration-ms=360000",
            "agent.same-subject-cooldown-seconds=0",
            "bionote.llm-config-path=../llm"
        })
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RUN_REAL_AGENT_WORKFLOW", matches = "true")
class RealAgentWorkflowSmokeTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired AgentRunService runs;
    @Autowired AgentRunWorker worker;
    @Autowired AgentChatUseCase chat;
    @Autowired AgentChatReferenceService references;

    @Test
    void realProviderCompletesReportsChatAttachmentReadAndFit() {
        Fixture fixture = fixture();

        AgentDtos.RunView recordRun =
                runs.createRecord(
                        fixture.user(),
                        fixture.record(),
                        new AgentDtos.CreateRunRequest(
                                "RECORD_SUMMARY", null, null, "总结当前实验状态和可核对证据"),
                        "real-record-" + UUID.randomUUID());
        recordRun = finish(fixture.user(), recordRun.id());
        assertThat(recordRun.status()).isEqualTo("SUCCEEDED");
        assertThat(recordRun.artifactId()).isNotNull();
        assertThat(runs.artifact(fixture.user(), recordRun.artifactId()).content().path("headline").asText())
                .isNotBlank();

        Instant now = Instant.now();
        AgentDtos.RunView projectRun =
                runs.createProject(
                        fixture.user(),
                        fixture.project(),
                        new AgentDtos.CreateRunRequest(
                                "PROJECT_PROGRESS",
                                now.minusSeconds(86400),
                                now.plusSeconds(60),
                                "概括项目进展、风险和下一步"),
                        "real-project-" + UUID.randomUUID());
        projectRun = finish(fixture.user(), projectRun.id());
        assertThat(projectRun.status()).isEqualTo("SUCCEEDED");
        assertThat(projectRun.artifactId()).isNotNull();

        AgentDtos.ChatReply recordReply =
                chat.chat(
                        fixture.user(),
                        fixture.record(),
                        new AgentDtos.ChatRequest(
                                "这条实验记录的标题和当前状态是什么？",
                                List.of(),
                                null,
                                List.of()));
        assertThat(recordReply.provider()).isEqualTo("openai-compatible");
        assertThat(recordReply.reply()).isNotBlank();

        byte[] csv =
                "time;value\n0;1\n1;3\n2;5\n3;7\n"
                        .getBytes(StandardCharsets.UTF_8);
        AgentDtos.ChatReferenceView reference =
                references.upload(
                        fixture.user(),
                        fixture.project(),
                        new MockMultipartFile("file", "real-fit.csv", "text/csv", csv));
        AgentDtos.ChatReply fitReply =
                chat.chatAboutProject(
                        fixture.user(),
                        fixture.project(),
                        new AgentDtos.ChatRequest(
                                "读取我附加的 real-fit.csv，使用 time 作为 x、value 作为 y 做线性拟合。必须调用 fit_data 并返回真实拟合结果。",
                                List.of(),
                                null,
                                List.of(reference.id())));
        assertThat(fitReply.reply()).isNotBlank();
        assertThat(fitReply.fit()).isNotNull();
        assertThat(fitReply.fit().n()).isEqualTo(4);
        assertThat(fitReply.fit().rSquared()).isGreaterThan(0.999);
    }

    private AgentDtos.RunView finish(UUID actor, UUID runId) {
        AgentDtos.RunView current = runs.get(actor, runId);
        for (int attempt = 0; attempt < 3 && !terminal(current.status()); attempt++) {
            worker.tick();
            current = runs.get(actor, runId);
        }
        return current;
    }

    private boolean terminal(String status) {
        return List.of("SUCCEEDED", "FAILED", "CANCELLED", "LIMIT_EXCEEDED", "INVALID_OUTPUT")
                .contains(status);
    }

    private Fixture fixture() {
        UUID user = UUID.randomUUID(), project = UUID.randomUUID(), record = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO users(id,display_name,email_normalized,password_hash,created_at,updated_at,version) VALUES(?,?,?,?,?,?,0)",
                user.toString(),
                "Real Agent Tester",
                "real-agent-" + user + "@example.com",
                "not-used",
                Timestamp.from(now),
                Timestamp.from(now));
        jdbc.update(
                "INSERT INTO projects(id,name,description,status,owner_id,created_at,updated_at,version,detailed_description) VALUES(?,?,?,?,?,?,?,0,?)",
                project.toString(),
                "真实 Agent 验证项目",
                "用于验证报告、问答和附件拟合",
                "ACTIVE",
                user.toString(),
                Timestamp.from(now),
                Timestamp.from(now),
                "项目包含一条进行中的 qPCR 实验记录");
        jdbc.update(
                "INSERT INTO project_members(project_id,user_id,role,joined_at) VALUES(?,?,?,?)",
                project.toString(),
                user.toString(),
                "OWNER",
                Timestamp.from(now));
        jdbc.update(
                """
                INSERT INTO experiment_records(
                  id,code,project_id,creator_id,title,experiment_type,experiment_date,purpose,status,
                  template_snapshot_json,field_values_json,content_json,content_html_sanitized,
                  content_plain_text,current_revision_no,version,created_at,updated_at,provisional)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,0,?,?,FALSE)
                """,
                record.toString(),
                "EXP-REAL-" + record.toString().substring(0, 8),
                project.toString(),
                user.toString(),
                "低氧条件 qPCR 验证",
                "qPCR",
                Date.valueOf(LocalDate.now()),
                "观察目标基因在低氧条件下的表达变化",
                "IN_PROGRESS",
                "{\"fields\":[]}",
                "{\"ct_value\":21.4}",
                "{}",
                "<p>已完成样本准备，等待复核。</p>",
                "已完成样本准备，等待复核。",
                Timestamp.from(now),
                Timestamp.from(now));
        return new Fixture(user, project, record);
    }

    private record Fixture(UUID user, UUID project, UUID record) {}
}
