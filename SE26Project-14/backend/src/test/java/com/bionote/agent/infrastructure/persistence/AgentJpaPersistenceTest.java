package com.bionote.agent.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class AgentJpaPersistenceTest {
    @Autowired TestEntityManager entityManager;
    @Autowired AgentRunJpaRepository runs;
    @Autowired AgentStepJpaRepository steps;
    @Autowired AgentArtifactJpaRepository artifacts;
    @Autowired PromptVersionJpaRepository prompts;
    @Autowired AgentChatReferenceJpaRepository chatReferences;

    private UUID userId;
    private UUID projectId;

    @BeforeEach
    void seedForeignKeys() {
        userId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        Instant now = Instant.now();
        entityManager
                .getEntityManager()
                .createNativeQuery(
                        """
                        INSERT INTO users(id,display_name,email_normalized,password_hash,created_at,updated_at,version)
                        VALUES(:id,'Agent JPA Test',:email,'hash',:now,:now,0)
                        """)
                .setParameter("id", userId.toString())
                .setParameter("email", userId + "@example.test")
                .setParameter("now", Timestamp.from(now))
                .executeUpdate();
        entityManager
                .getEntityManager()
                .createNativeQuery(
                        """
                        INSERT INTO projects(id,name,status,owner_id,created_at,updated_at,version)
                        VALUES(:id,:name,'ACTIVE',:owner,:now,:now,0)
                        """)
                .setParameter("id", projectId.toString())
                .setParameter("name", "Agent JPA " + projectId)
                .setParameter("owner", userId.toString())
                .setParameter("now", Timestamp.from(now))
                .executeUpdate();
        entityManager.flush();
    }

    @Test
    void promptActiveNameIsUnique() {
        prompts.saveAndFlush(prompt(1, true, "project-progress"));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> prompts.saveAndFlush(prompt(2, true, "project-progress")));
    }

    @Test
    void queuedRunCanBeClaimedOnlyOnce() {
        AgentRunEntity run = run();
        Instant claimedAt = Instant.now();

        assertEquals(1, runs.claim(run.id.toString(), run.version, claimedAt));
        assertEquals(0, runs.claim(run.id.toString(), run.version, claimedAt.plusSeconds(1)));

        AgentRunEntity claimed = runs.findById(run.id).orElseThrow();
        assertEquals("RUNNING", claimed.status);
        assertEquals(1L, claimed.version);
        assertTrue(
                Math.abs(ChronoUnit.MICROS.between(claimedAt, claimed.startedAt)) <= 1,
                "database timestamp rounding must stay within one microsecond");
    }

    @Test
    void stepAppendPreservesOrderAndUpdatesRunMetrics() {
        AgentRunEntity run = run();
        JpaAgentStepAdapter adapter = new JpaAgentStepAdapter(steps, runs, new ObjectMapper());

        adapter.append(
                run.id,
                "MODEL",
                null,
                null,
                new ObjectMapper().createObjectNode().put("answer", 1),
                10,
                7,
                3);
        adapter.append(
                run.id,
                "TOOL",
                "lookup",
                new ObjectMapper().createObjectNode().put("id", 1),
                null,
                20,
                5,
                2);

        var stored = adapter.list(run.id);
        assertEquals(2, stored.size());
        assertEquals(1, stored.get(0).stepNo());
        assertEquals(2, stored.get(1).stepNo());
        AgentRunEntity updated = runs.findById(run.id).orElseThrow();
        assertEquals(2, updated.stepCount);
        assertEquals(12, updated.inputTokens);
        assertEquals(5, updated.outputTokens);
    }

    @Test
    void artifactIsUniquePerRun() {
        AgentRunEntity run = run();
        artifacts.saveAndFlush(artifact(run.id));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> artifacts.saveAndFlush(artifact(run.id)));
    }

    @Test
    void chatReferenceRequiresMatchingOwnerAndUnexpiredTimestamp() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        chatReferences.saveAndFlush(
                new AgentChatReferenceEntity(
                        id,
                        projectId,
                        userId,
                        UUID.randomUUID().toString(),
                        "reference.csv",
                        "text/csv",
                        12,
                        "{}",
                        now,
                        now.plus(24, ChronoUnit.HOURS)));

        assertTrue(
                chatReferences
                        .findByIdAndProjectIdAndUploadedByAndExpiresAtAfter(
                                id, projectId, userId, now)
                        .isPresent());
        assertFalse(
                chatReferences
                        .findByIdAndProjectIdAndUploadedByAndExpiresAtAfter(
                                id, projectId, UUID.randomUUID(), now)
                        .isPresent());
        assertFalse(
                chatReferences
                        .findByIdAndProjectIdAndExpiresAtAfter(
                                id, projectId, now.plus(25, ChronoUnit.HOURS))
                        .isPresent());
    }

    private PromptVersionEntity prompt(int version, boolean active, String activeNameKey) {
        return new PromptVersionEntity(
                UUID.randomUUID(),
                "project-progress",
                version,
                "template",
                "{}",
                "{}",
                "a".repeat(64),
                active,
                activeNameKey,
                Instant.now());
    }

    private AgentRunEntity run() {
        PromptVersionEntity prompt = prompt(1, true, "project-progress");
        prompts.saveAndFlush(prompt);
        AgentRunEntity run =
                new AgentRunEntity(
                        UUID.randomUUID(),
                        "PROJECT_PROGRESS",
                        "PROJECT",
                        projectId,
                        projectId,
                        null,
                        userId,
                        "MANUAL",
                        "fake",
                        "fake-model",
                        prompt.id,
                        null,
                        "test:" + UUID.randomUUID(),
                        "{}",
                        "b".repeat(64),
                        "{}",
                        "{}",
                        Instant.now());
        return runs.saveAndFlush(run);
    }

    private AgentArtifactEntity artifact(UUID runId) {
        return new AgentArtifactEntity(
                UUID.randomUUID(),
                runId,
                "PROJECT_PROGRESS",
                projectId,
                null,
                "{}",
                "[]",
                "c".repeat(64),
                Instant.now());
    }
}
