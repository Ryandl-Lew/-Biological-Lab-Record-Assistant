package com.bionote.agent.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bionote.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {"agent.enabled=true", "agent.provider=fake"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentChatApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;

    @BeforeEach
    void clean() {
        jdbc.update("UPDATE experiment_records SET current_review_id=NULL,final_revision_id=NULL");
        jdbc.update("DELETE FROM agent_artifacts");
        jdbc.update("DELETE FROM agent_steps");
        jdbc.update("DELETE FROM agent_runs");
        jdbc.update("DELETE FROM record_restore_operations");
        jdbc.update("DELETE FROM revision_attachments");
        jdbc.update("DELETE FROM reviews");
        jdbc.update("DELETE FROM record_revisions");
        jdbc.update("DELETE FROM attachments");
        jdbc.update("DELETE FROM experiment_records");
        jdbc.update("DELETE FROM audit_events");
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM project_invitations");
        jdbc.update("DELETE FROM project_members");
        jdbc.update("DELETE FROM projects");
        users.deleteAll();
    }

    @Test
    void memberCanChatOutsiderCannotAndDisabledIsUnavailable() throws Exception {
        Fixture f = fixture();
        mvc.perform(
                        post("/api/v1/records/{id}/agent-chat", f.record)
                                .header("Authorization", bearer(f.memberToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"message\":\"这条记录目前是什么状态？\",\"history\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider").value("fake"))
                .andExpect(jsonPath("$.data.reply").isNotEmpty());

        mvc.perform(
                        post("/api/v1/projects/{id}/agent-chat", f.project)
                                .header("Authorization", bearer(f.memberToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"message\":\"这个项目现在有多少条记录？\",\"history\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider").value("fake"))
                .andExpect(jsonPath("$.data.reply").isNotEmpty());

        mvc.perform(
                        post("/api/v1/records/{id}/agent-chat", f.record)
                                .header("Authorization", bearer(f.outsiderToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"message\":\"hello\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mvc.perform(
                        post("/api/v1/projects/{id}/agent-chat", f.project)
                                .header("Authorization", bearer(f.outsiderToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"message\":\"hello\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void memberGetsFitProposalThenConfirmsAndAutoCompareWorks() throws Exception {
        Fixture f = fixtureWithNumericFields();
        MvcResult proposed =
                mvc.perform(
                                post("/api/v1/projects/{id}/agent-chat", f.project)
                                        .header("Authorization", bearer(f.memberToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"message\":\"拟合 y=a+b*x；xField=concentration；yField=ct\",\"history\":[]}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.fit").value(org.hamcrest.Matchers.nullValue()))
                        .andExpect(
                                jsonPath("$.data.proposal.equation")
                                        .value(org.hamcrest.Matchers.containsString("b*x")))
                        .andExpect(jsonPath("$.data.proposal.xSpec").value("concentration"))
                        .andExpect(jsonPath("$.data.proposal.ySpec").value("ct"))
                        .andExpect(jsonPath("$.data.proposal.autoCompare").value(false))
                        .andReturn();
        JsonNode proposal =
                json.readTree(proposed.getResponse().getContentAsString())
                        .path("data")
                        .path("proposal");

        String confirmBody =
                json.writeValueAsString(
                        java.util.Map.of(
                                "message", "确认按拟定方案拟合",
                                "history", java.util.List.of(),
                                "fitConfirm", json.convertValue(proposal, Object.class)));
        mvc.perform(
                        post("/api/v1/projects/{id}/agent-chat", f.project)
                                .header("Authorization", bearer(f.memberToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(confirmBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proposal").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.fit.equation").value("y = a+b*x"))
                .andExpect(jsonPath("$.data.fit.n").value(4))
                .andExpect(jsonPath("$.data.fit.parameters.a").isNumber())
                .andExpect(jsonPath("$.data.fit.parameters.b").isNumber());

        MvcResult autoProposed =
                mvc.perform(
                                post("/api/v1/projects/{id}/agent-chat", f.project)
                                        .header("Authorization", bearer(f.memberToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"message\":\"用 concentration 和 ct 做拟合，方程不太确定，试试看\",\"history\":[]}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.fit").value(org.hamcrest.Matchers.nullValue()))
                        .andExpect(jsonPath("$.data.proposal.autoCompare").value(true))
                        .andExpect(jsonPath("$.data.proposal.xSpec").value("concentration"))
                        .andExpect(jsonPath("$.data.proposal.ySpec").value("ct"))
                        .andReturn();
        JsonNode autoProposal =
                json.readTree(autoProposed.getResponse().getContentAsString())
                        .path("data")
                        .path("proposal");
        String autoConfirm =
                json.writeValueAsString(
                        java.util.Map.of(
                                "message", "确认按拟定方案拟合",
                                "history", java.util.List.of(),
                                "fitConfirm", json.convertValue(autoProposal, Object.class)));
        mvc.perform(
                        post("/api/v1/projects/{id}/agent-chat", f.project)
                                .header("Authorization", bearer(f.memberToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(autoConfirm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fit.n").value(4))
                .andExpect(jsonPath("$.data.fit.comparisons").isArray())
                .andExpect(jsonPath("$.data.fit.comparisons[0].equation").isNotEmpty());

        mvc.perform(
                        post("/api/v1/projects/{id}/agent-chat", f.project)
                                .header("Authorization", bearer(f.memberToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"message\":\"请拟合 y=a+b*x\",\"history\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fit").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(
                        jsonPath("$.data.reply")
                                .value(
                                        org.hamcrest.Matchers.anyOf(
                                                org.hamcrest.Matchers.containsString("自变量"),
                                                org.hamcrest.Matchers.containsString("因变量"),
                                                org.hamcrest.Matchers.containsString("确认"))));

        mvc.perform(
                        post("/api/v1/projects/{id}/agent-chat", f.project)
                                .header("Authorization", bearer(f.outsiderToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"message\":\"拟合 y=a+b*x；xField=concentration；yField=ct\",\"history\":[]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private Fixture fixture() throws Exception {
        User owner = register("Owner", "owner-" + UUID.randomUUID() + "@example.com");
        User creator = register("Creator", "creator-" + UUID.randomUUID() + "@example.com");
        User member = register("Member", "member-" + UUID.randomUUID() + "@example.com");
        User outsider = register("Outside", "outside-" + UUID.randomUUID() + "@example.com");
        UUID project =
                UUID.fromString(
                        value(
                                mvc.perform(
                                                post("/api/v1/projects")
                                                        .header(
                                                                "Authorization",
                                                                bearer(owner.token))
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .content("{\"name\":\"Chat Project\"}"))
                                        .andExpect(status().isOk())
                                        .andReturn(),
                                "data.id"));
        jdbc.update(
                "INSERT INTO project_members(project_id,user_id,role,joined_at) VALUES(?,?, 'MEMBER',CURRENT_TIMESTAMP)",
                project.toString(),
                creator.id.toString());
        jdbc.update(
                "INSERT INTO project_members(project_id,user_id,role,joined_at) VALUES(?,?, 'MEMBER',CURRENT_TIMESTAMP)",
                project.toString(),
                member.id.toString());
        UUID record =
                UUID.fromString(
                        value(
                                mvc.perform(
                                                post("/api/v1/records")
                                                        .header(
                                                                "Authorization",
                                                                bearer(creator.token))
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .content(
                                                                "{\"projectId\":\""
                                                                        + project
                                                                        + "\",\"title\":\"Chat record\",\"experimentType\":\"PCR\",\"experimentDate\":\"2026-07-26\",\"purpose\":\"Chat context\"}"))
                                        .andExpect(status().isOk())
                                        .andReturn(),
                                "data.id"));
        return new Fixture(member.token, outsider.token, project, record);
    }

    private Fixture fixtureWithNumericFields() throws Exception {
        Fixture base = fixture();
        double[][] pairs = {{1, 3}, {2, 5}, {3, 7}, {4, 9}};
        for (int i = 0; i < pairs.length; i++) {
            UUID id = UUID.randomUUID();
            String code = "EXP-FIT-" + (i + 1);
            String fields = "{\"concentration\":" + pairs[i][0] + ",\"ct\":" + pairs[i][1] + "}";
            jdbc.update(
                    """
                    INSERT INTO experiment_records(id,code,project_id,creator_id,title,experiment_type,experiment_date,purpose,status,
                    template_snapshot_json,field_values_json,content_json,content_html_sanitized,content_plain_text,
                    current_revision_no,version,created_at,updated_at,provisional)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE)
                    """,
                    id.toString(),
                    code,
                    base.project.toString(),
                    jdbc.queryForObject(
                            "SELECT creator_id FROM experiment_records WHERE id=?",
                            String.class,
                            base.record.toString()),
                    "Fit sample " + (i + 1),
                    "qPCR",
                    java.time.LocalDate.of(2026, 7, 20),
                    "fit",
                    "COMPLETED",
                    "{\"fields\":[]}",
                    fields,
                    "{}",
                    "",
                    "");
        }
        return base;
    }

    private User register(String name, String email) throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/v1/auth/register")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"displayName\":\""
                                                        + name
                                                        + "\",\"email\":\""
                                                        + email
                                                        + "\",\"password\":\"Password123!\"}"))
                        .andExpect(status().isOk())
                        .andReturn();
        return new User(
                UUID.fromString(value(result, "data.user.id")), value(result, "data.accessToken"));
    }

    private String value(MvcResult result, String path) throws Exception {
        JsonNode node = json.readTree(result.getResponse().getContentAsString());
        for (String part : path.split("\\.")) node = node.get(part);
        return node.asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record User(UUID id, String token) {}

    private record Fixture(String memberToken, String outsiderToken, UUID project, UUID record) {}
}
