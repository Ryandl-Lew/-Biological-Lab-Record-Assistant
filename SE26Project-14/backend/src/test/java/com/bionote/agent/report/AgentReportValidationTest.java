package com.bionote.agent.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.bionote.agent.prompt.PromptVersion;
import com.bionote.agent.prompt.PromptVersionService;
import com.bionote.agent.runtime.AgentLimits;
import com.bionote.agent.runtime.AgentRunContext;
import com.bionote.agent.runtime.AgentRunMemory;
import com.bionote.agent.runtime.AgentRunRecord;
import com.bionote.agent.runtime.AgentRunRepository;
import com.bionote.agent.validation.AgentResultValidator;
import com.bionote.agent.validation.ValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AgentReportValidationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired AgentRunRepository runs;
    @Autowired PromptVersionService prompts;
    @Autowired AgentResultValidator validator;
    private UUID actor, project, run;
    private PromptVersion prompt;
    private AgentRunMemory memory;
    private AgentRunContext context;

    @BeforeEach
    void setup() {
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
        jdbc.update("DELETE FROM users");
        actor = UUID.randomUUID();
        project = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO users(id,display_name,email_normalized,password_hash,created_at,updated_at,version) VALUES(?,?,?,'hash',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",
                actor.toString(),
                "Validator",
                "validator@example.com");
        jdbc.update(
                "INSERT INTO projects(id,name,status,owner_id,created_at,updated_at,version) VALUES(?,?,'ACTIVE',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",
                project.toString(),
                "Validation Project",
                actor.toString());
        jdbc.update(
                "INSERT INTO project_members(project_id,user_id,role,joined_at) VALUES(?,?, 'OWNER',CURRENT_TIMESTAMP)",
                project.toString(),
                actor.toString());
        prompt = prompts.active("project-progress");
        run = UUID.randomUUID();
        AgentRunRecord record =
                runs.enqueue(
                        run,
                        "PROJECT_PROGRESS",
                        "PROJECT",
                        project,
                        project,
                        null,
                        actor,
                        "MANUAL",
                        "fake",
                        "fake-deterministic-v1",
                        prompt.id(),
                        null,
                        UUID.randomUUID().toString(),
                        "{}",
                        "a".repeat(64),
                        "{}",
                        "{\"maxSteps\":30,\"maxToolCalls\":12,\"maxModelCalls\":10,\"maxDurationMs\":30000,\"maxOutputTokens\":1000,\"maxRepairTurns\":1}");
        memory = new AgentRunMemory();
        memory.addEvidenceCandidates(
                java.util.List.of(
                        EvidenceCandidate.project(project, "Validation Project").encode(json)));
        context =
                new AgentRunContext(
                        record,
                        prompt,
                        memory,
                        AgentLimits.parse(json, record.limitsJson()),
                        Instant.now().plusSeconds(30));
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM agent_artifacts");
        jdbc.update("DELETE FROM agent_steps");
        jdbc.update("DELETE FROM agent_runs");
    }

    @Test
    void validEvidencePassesWhileUnknownRefsHallucinationsSecretsAndRevocationFail() {
        assertThat(validate(artifact("e1", project.toString(), "PROJECT", false)).valid()).isTrue();
        JsonNode unknown = artifact("missing", project.toString(), "PROJECT", false);
        assertThat(validate(unknown).valid()).isFalse();
        JsonNode hallucinated = artifact("e1", UUID.randomUUID().toString(), "PROJECT", false);
        assertThat(validate(hallucinated).errors())
                .anyMatch(value -> value.startsWith("EVIDENCE:"));
        JsonNode secret = artifact("e1", project.toString(), "PROJECT", true);
        assertThat(validate(secret).errors()).anyMatch(value -> value.contains("email"));
        jdbc.update(
                "DELETE FROM project_members WHERE project_id=? AND user_id=?",
                project.toString(),
                actor.toString());
        assertThat(validate(artifact("e1", project.toString(), "PROJECT", false)).errors())
                .anyMatch(value -> value.contains("no longer"));
    }

    @Test
    void revisionDiffEvidenceRejectsChangedSourceHash() {
        UUID record = UUID.randomUUID(), from = UUID.randomUUID(), to = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO experiment_records(id,code,project_id,creator_id,title,experiment_type,experiment_date,purpose,status,template_snapshot_json,field_values_json,content_json,content_html_sanitized,content_plain_text,current_revision_no,version,created_at,updated_at,provisional) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,2,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE)",
                record.toString(),
                "EXP-DIFF",
                project.toString(),
                actor.toString(),
                "Diff record",
                "PCR",
                LocalDate.now(),
                "purpose",
                "IN_PROGRESS",
                "{\"fields\":[]}",
                "{}",
                "{}",
                "",
                "body");
        jdbc.update(
                "INSERT INTO record_revisions(id,record_id,revision_no,snapshot_json,content_hash,submit_note,submitted_by,submitted_at,snapshot_schema_version) VALUES(?,?,?,?,?,?,?,CURRENT_TIMESTAMP,1)",
                from.toString(),
                record.toString(),
                1,
                "{}",
                "a".repeat(64),
                "R1",
                actor.toString());
        jdbc.update(
                "INSERT INTO record_revisions(id,record_id,revision_no,snapshot_json,content_hash,submit_note,submitted_by,submitted_at,snapshot_schema_version) VALUES(?,?,?,?,?,?,?,CURRENT_TIMESTAMP,1)",
                to.toString(),
                record.toString(),
                2,
                "{}",
                "b".repeat(64),
                "R2",
                actor.toString());
        EvidenceCandidate candidate =
                EvidenceCandidate.diff(project, record, from, to, "0".repeat(64), "Changed diff");
        memory.addEvidenceCandidates(java.util.List.of(candidate.encode(json)));
        JsonNode value = artifact("e1", candidate.id(), "REVISION_DIFF", false);
        JsonNode evidence = value.path("evidence").get(0);
        ((com.fasterxml.jackson.databind.node.ObjectNode) evidence)
                .put("recordId", record.toString())
                .put("fromRevisionId", from.toString())
                .put("toRevisionId", to.toString());
        assertThat(validate(value).errors())
                .contains("EVIDENCE: revision diff source hash changed");
    }

    private ValidationResult validate(JsonNode artifact) {
        return validator.validate(context, artifact, prompt.outputSchema());
    }

    private JsonNode artifact(String ref, String id, String type, boolean email) {
        var root = json.createObjectNode();
        root.put("schemaVersion", 1)
                .put("headline", "Validation")
                .put("executiveSummary", email ? "contact validator@example.com" : "Summary");
        root.set(
                "progress",
                json.createArrayNode()
                        .add(
                                json.createObjectNode()
                                        .put("id", "p1")
                                        .put("statement", "Fact")
                                        .set("evidenceRefs", json.createArrayNode().add(ref))));
        root.set("risks", json.createArrayNode());
        root.set("nextActions", json.createArrayNode());
        root.set(
                "evidence",
                json.createArrayNode()
                        .add(
                                json.createObjectNode()
                                        .put("ref", "e1")
                                        .put("type", type)
                                        .put("id", id)
                                        .put("label", "Evidence")));
        root.set("limitations", json.createArrayNode());
        root.set(
                "period",
                json.createObjectNode()
                        .put("start", "2026-07-20T00:00:00Z")
                        .put("end", "2026-07-27T00:00:00Z"));
        return root;
    }
}
