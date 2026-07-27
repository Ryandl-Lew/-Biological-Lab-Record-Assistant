package com.bionote.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:file:./target/local-startup-test/bionote-rich-seed-v12-final;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "bionote.dev-seed-enabled=true",
        "bionote.upload-root=${java.io.tmpdir}/bionote-local-startup-v12-final-uploads"
})
class LocalStartupIntegrationTest {
    @Autowired Environment environment;
    @Autowired JdbcTemplate jdbc;
    @Autowired DemoDataService demoDataService;

    @Test
    void localProfileStartsWithFlywayAndRealisticIdempotentDevelopmentSeed() {
        assertThat(environment.getDefaultProfiles()).contains("local");
        assertThat(jdbc.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version='8'",
                Boolean.class
        )).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM projects", Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM projects WHERE status='ARCHIVED'", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM experiment_records", Long.class)).isEqualTo(11);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM experiment_records WHERE status='COMPLETED'", Long.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM experiment_records WHERE status='IN_REVIEW'", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM experiment_records WHERE status='CHANGES_REQUESTED'", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM experiment_records WHERE status='IN_PROGRESS'", Long.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attachments", Long.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM record_revisions", Long.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM experiment_records r WHERE r.status='CHANGES_REQUESTED' AND r.current_revision_no=2", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM projects p WHERE (SELECT COUNT(*) FROM audit_events a WHERE a.project_id=p.id) >= 10", Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM template_fields WHERE template_id='10000000-0000-0000-0000-000000000001'", Long.class)).isEqualTo(14);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM template_fields WHERE template_id='10000000-0000-0000-0000-000000000002'", Long.class)).isEqualTo(16);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM template_fields WHERE template_id='10000000-0000-0000-0000-000000000003'", Long.class)).isEqualTo(16);

        demoDataService.seed();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM projects", Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM experiment_records", Long.class)).isEqualTo(11);
    }
}
