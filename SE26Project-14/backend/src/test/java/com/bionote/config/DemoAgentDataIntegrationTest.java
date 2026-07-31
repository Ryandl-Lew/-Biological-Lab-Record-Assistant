package com.bionote.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:bionote-demo-agent-v12;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
            "bionote.dev-seed-enabled=true",
            "bionote.upload-root=${java.io.tmpdir}/bionote-demo-agent-v12-uploads",
            "agent.enabled=true",
            "agent.provider=fake",
            "agent.worker-poll-ms=60000",
            "agent.same-subject-cooldown-seconds=0"
        })
@ActiveProfiles("local")
class DemoAgentDataIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @Test
    void phase2DemoRunsComeFromRuntimeAndDatabaseInvariantsHold() throws Exception {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agent_runs", Long.class)).isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM agent_runs WHERE status='SUCCEEDED'",
                                Long.class))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM agent_runs WHERE status='INVALID_OUTPUT' AND error_code='AGENT_EVIDENCE_INVALID'",
                                Long.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agent_artifacts", Long.class))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM (SELECT r.id FROM agent_runs r LEFT JOIN agent_artifacts a ON a.run_id=r.id WHERE r.status='SUCCEEDED' GROUP BY r.id HAVING COUNT(a.id)<>1) bad",
                                Long.class))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM agent_runs r JOIN agent_artifacts a ON a.run_id=r.id WHERE r.status<>'SUCCEEDED'",
                                Long.class))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM agent_runs r WHERE r.step_count<>(SELECT COUNT(*) FROM agent_steps s WHERE s.run_id=r.id)",
                                Long.class))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM (SELECT record_id,COUNT(*) c,MAX(revision_no) m,MIN(revision_no) n FROM record_revisions GROUP BY record_id HAVING c<>m OR n<>1) gaps",
                                Long.class))
                .isZero();

        var row =
                jdbc.queryForMap(
                        "SELECT a.project_id,a.evidence_json FROM agent_artifacts a JOIN agent_runs r ON r.id=a.run_id WHERE r.status='SUCCEEDED'");
        JsonNode evidence = json.readTree(row.get("evidence_json").toString());
        assertThat(evidence).isNotEmpty();
        assertThat(evidence)
                .allSatisfy(
                        item ->
                                assertThat(evidenceProject(item))
                                        .isEqualTo(row.get("project_id").toString()));
    }

    private String evidenceProject(JsonNode item) {
        String id = item.path("id").asText();
        return switch (item.path("type").asText()) {
            case "PROJECT" -> id;
            case "AUDIT_EVENT" ->
                    jdbc.queryForObject(
                            "SELECT project_id FROM audit_events WHERE id=?", String.class, id);
            case "RECORD" ->
                    jdbc.queryForObject(
                            "SELECT project_id FROM experiment_records WHERE id=?",
                            String.class,
                            id);
            case "REVISION" ->
                    jdbc.queryForObject(
                            "SELECT r.project_id FROM record_revisions rv JOIN experiment_records r ON r.id=rv.record_id WHERE rv.id=?",
                            String.class,
                            id);
            case "REVIEW" ->
                    jdbc.queryForObject(
                            "SELECT r.project_id FROM reviews v JOIN experiment_records r ON r.id=v.record_id WHERE v.id=?",
                            String.class,
                            id);
            default ->
                    throw new AssertionError(
                            "Unsupported demo evidence type: " + item.path("type").asText());
        };
    }
}
