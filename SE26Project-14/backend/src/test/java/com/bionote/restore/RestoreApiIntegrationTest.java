package com.bionote.restore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bionote.attachment.AttachmentStorageService;
import com.bionote.collaboration.event.DomainEventHandler;
import com.bionote.collaboration.event.RecordRevisionRestoredEvent;
import com.bionote.common.ApiException;
import com.bionote.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RestoreApiIntegrationTest.FailureConfig.class)
class RestoreApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired AttachmentStorageService storage;
    @Autowired RestorePreviewService previews;
    @Autowired RestoreExecutionService executions;
    @Autowired AtomicBoolean failRestoreAudit;
    private final List<String> storedKeys = new ArrayList<>();

    @BeforeEach
    void clean() {
        failRestoreAudit.set(false);
        jdbc.update("UPDATE experiment_records SET current_review_id=NULL,final_revision_id=NULL");
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

    @AfterEach
    void files() {
        storedKeys.forEach(storage::deleteQuietly);
        storedKeys.clear();
        failRestoreAudit.set(false);
    }

    @Test
    void restoreIsTransactionalIdempotentAndNextSubmissionBecomesR3() throws Exception {
        Fixture f = fixture();
        String beforeR1 =
                jdbc.queryForObject(
                        "SELECT snapshot_json FROM record_revisions WHERE id=?",
                        String.class,
                        f.r1.toString());
        MvcResult preview = preview(f, false, 5);
        String token = value(preview, "data.previewToken");
        assertThat(value(preview, "data.diff.from.type")).isEqualTo("WORKING_COPY");
        assertThat(
                        json.readTree(preview.getResponse().getContentAsByteArray())
                                .at("/data/attachmentPlan/droppedFileFieldReferences/files")
                                .size())
                .isEqualTo(1);
        String key = UUID.randomUUID().toString();
        MvcResult restored = execute(f, false, 5, token, key, 200);
        String operationId = value(restored, "data.operation.id");
        assertThat(value(restored, "data.record.title")).isEqualTo("R1 标题");
        assertThat(value(restored, "data.record.status")).isEqualTo("CHANGES_REQUESTED");
        assertThat(value(restored, "data.record.version")).isEqualTo("6");
        assertThat(
                        json.readTree(restored.getResponse().getContentAsByteArray())
                                .at("/data/record/fieldValues/files")
                                .size())
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM record_restore_operations", Integer.class))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM audit_events WHERE event_type='RECORD_REVISION_RESTORED'",
                                Integer.class))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT snapshot_json FROM record_revisions WHERE id=?",
                                String.class,
                                f.r1.toString()))
                .isEqualTo(beforeR1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM record_revisions WHERE record_id=?",
                                Integer.class,
                                f.record.toString()))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM reviews WHERE record_id=?",
                                Integer.class,
                                f.record.toString()))
                .isEqualTo(2);
        String audit =
                jdbc.queryForObject(
                        "SELECT metadata_json FROM audit_events WHERE event_type='RECORD_REVISION_RESTORED'",
                        String.class);
        assertThat(audit).doesNotContain("R1 正文", f.oldStorageKey, f.currentStorageKey);

        MvcResult retry = execute(f, false, 5, token, key, 200);
        assertThat(value(retry, "data.operation.id")).isEqualTo(operationId);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM record_restore_operations", Integer.class))
                .isEqualTo(1);
        execute(f, true, 5, token, key, 409).getResponse();

        mvc.perform(
                        post("/api/v1/records/{id}/submissions", f.record)
                                .header("Authorization", bearer(f.ownerToken))
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"reviewerId\":\""
                                                + f.reviewer
                                                + "\",\"expectedRecordVersion\":6,\"submitNote\":\"恢复后提交\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revisionNo").value(3));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT MAX(revision_no) FROM record_revisions WHERE record_id=?",
                                Integer.class,
                                f.record.toString()))
                .isEqualTo(3);
    }

    @Test
    void fullAttachmentRestoreExactlyMatchesSourceAndNoopIsRejected() throws Exception {
        Fixture f = fixture();
        MvcResult preview = preview(f, true, 5);
        String token = value(preview, "data.previewToken");
        assertThat(value(preview, "data.capabilities.canExecute")).isEqualTo("true");
        execute(f, true, 5, token, UUID.randomUUID().toString(), 200);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT deleted_at IS NULL FROM attachments WHERE id=?",
                                Boolean.class,
                                f.oldAttachment.toString()))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT deleted_at IS NOT NULL FROM attachments WHERE id=?",
                                Boolean.class,
                                f.currentAttachment.toString()))
                .isTrue();
        String values =
                jdbc.queryForObject(
                        "SELECT field_values_json FROM experiment_records WHERE id=?",
                        String.class,
                        f.record.toString());
        assertThat(values)
                .contains(f.oldAttachment.toString())
                .doesNotContain(f.currentAttachment.toString());
        MvcResult noChangePreview = preview(f, true, 6);
        assertThat(value(noChangePreview, "data.capabilities.canExecute")).isEqualTo("false");
        execute(
                f,
                true,
                6,
                value(noChangePreview, "data.previewToken"),
                UUID.randomUUID().toString(),
                409);
    }

    @Test
    void staleUnauthorizedAndCrossRecordRequestsAreRejectedWithoutDisclosure() throws Exception {
        Fixture f = fixture();
        User outsider = register("外部用户", "outside-" + UUID.randomUUID() + "@example.com");
        mvc.perform(
                        post("/api/v1/records/{id}/restore-preview", f.record)
                                .header("Authorization", bearer(outsider.token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(previewBody(f.r1, 5, false)))
                .andExpect(status().isNotFound());
        jdbc.update(
                "INSERT INTO project_members(project_id,user_id,role,joined_at) VALUES(?,?, 'OWNER',?)",
                f.project.toString(),
                outsider.id.toString(),
                Timestamp.from(Instant.now()));
        mvc.perform(
                        post("/api/v1/records/{id}/restore-preview", f.record)
                                .header("Authorization", bearer(outsider.token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(previewBody(f.r1, 5, false)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(
                        post("/api/v1/records/{id}/restore-preview", f.record)
                                .header("Authorization", bearer(f.ownerToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(previewBody(f.r1, 4, false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPTIMISTIC_LOCK_CONFLICT"));
        UUID otherRecord = UUID.fromString(createRecord(f.ownerToken, f.project, "其他记录"));
        mvc.perform(
                        post("/api/v1/records/{id}/restore-preview", otherRecord)
                                .header("Authorization", bearer(f.ownerToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(previewBody(f.r1, 0, false)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVISION_NOT_FOUND"));
        mvc.perform(
                        get("/api/v1/records/{id}/restore-operations", f.record)
                                .header(
                                        "Authorization",
                                        bearer(
                                                register(
                                                                "另一外部用户",
                                                                "other-"
                                                                        + UUID.randomUUID()
                                                                        + "@example.com")
                                                        .token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingPhysicalSourceFileBlocksFullRestoreButContentOnlyStillWorks() throws Exception {
        Fixture f = fixture();
        storage.deleteQuietly(f.oldStorageKey);
        MvcResult full = preview(f, true, 5);
        assertThat(value(full, "data.capabilities.canExecute")).isEqualTo("false");
        assertThat(
                        json.readTree(full.getResponse().getContentAsByteArray())
                                .at("/data/attachmentPlan/missingPhysicalFiles")
                                .size())
                .isEqualTo(1);
        execute(f, true, 5, value(full, "data.previewToken"), UUID.randomUUID().toString(), 409);
        MvcResult contentOnly = preview(f, false, 5);
        assertThat(value(contentOnly, "data.capabilities.canExecute")).isEqualTo("true");
        execute(
                f,
                false,
                5,
                value(contentOnly, "data.previewToken"),
                UUID.randomUUID().toString(),
                200);
    }

    @Test
    void concurrentRestoresWithSameExpectedVersionAllowOnlyOneSuccess() throws Exception {
        Fixture f = fixture();
        RestoreDtos.Preview preview =
                previews.preview(
                        f.owner, f.record, new RestoreDtos.PreviewRequest(f.r1, 5L, false));
        RestoreDtos.ExecuteRequest request =
                new RestoreDtos.ExecuteRequest(f.r1, 5L, false, preview.previewToken());
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Object>> futures =
                List.of(UUID.randomUUID(), UUID.randomUUID()).stream()
                        .map(
                                key ->
                                        CompletableFuture.supplyAsync(
                                                () -> {
                                                    try {
                                                        start.await(5, TimeUnit.SECONDS);
                                                        return executions.restore(
                                                                f.owner,
                                                                f.record,
                                                                request,
                                                                key.toString());
                                                    } catch (Exception e) {
                                                        return e;
                                                    }
                                                }))
                        .toList();
        start.countDown();
        List<Object> results = futures.stream().map(CompletableFuture::join).toList();
        assertThat(results.stream().filter(RestoreDtos.Result.class::isInstance).count())
                .isEqualTo(1);
        assertThat(
                        results.stream()
                                .filter(ApiException.class::isInstance)
                                .map(ApiException.class::cast)
                                .map(ApiException::code)
                                .toList())
                .containsExactly("RESTORE_PREVIEW_STALE");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM record_restore_operations WHERE record_id=?",
                                Integer.class,
                                f.record.toString()))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT version FROM experiment_records WHERE id=?",
                                Long.class,
                                f.record.toString()))
                .isEqualTo(6L);
    }

    @Test
    void auditFailureRollsBackRecordAttachmentsVersionAndOperation() throws Exception {
        Fixture f = fixture();
        MvcResult preview = preview(f, true, 5);
        failRestoreAudit.set(true);
        mvc.perform(
                        post("/api/v1/records/{id}/restore", f.record)
                                .header("Authorization", bearer(f.ownerToken))
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        executeBody(
                                                f.r1,
                                                5,
                                                true,
                                                value(preview, "data.previewToken"))))
                .andExpect(status().is5xxServerError());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT version FROM experiment_records WHERE id=?",
                                Long.class,
                                f.record.toString()))
                .isEqualTo(5L);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT title FROM experiment_records WHERE id=?",
                                String.class,
                                f.record.toString()))
                .isEqualTo("当前工作副本");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT deleted_at IS NOT NULL FROM attachments WHERE id=?",
                                Boolean.class,
                                f.oldAttachment.toString()))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT deleted_at IS NULL FROM attachments WHERE id=?",
                                Boolean.class,
                                f.currentAttachment.toString()))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM record_restore_operations", Integer.class))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM audit_events WHERE event_type='RECORD_REVISION_RESTORED'",
                                Integer.class))
                .isZero();
    }

    private Fixture fixture() throws Exception {
        User owner = register("创建者", "owner-" + UUID.randomUUID() + "@example.com");
        UUID project =
                UUID.fromString(
                        value(
                                mvc.perform(
                                                post("/api/v1/projects")
                                                        .header(
                                                                "Authorization",
                                                                bearer(owner.token))
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .content("{\"name\":\"Restore 项目\"}"))
                                        .andExpect(status().isOk())
                                        .andReturn(),
                                "data.id"));
        UUID record = UUID.fromString(createRecord(owner.token, project, "当前工作副本"));
        User reviewer = register("审核人", "reviewer-" + UUID.randomUUID() + "@example.com");
        jdbc.update(
                "INSERT INTO project_members(project_id,user_id,role,joined_at) VALUES(?,?, 'REVIEWER',?)",
                project.toString(),
                reviewer.id.toString(),
                Timestamp.from(Instant.now()));
        Attachment old = attachment(record, owner.id, "old.txt", "old revision file", true),
                current = attachment(record, owner.id, "current.txt", "current file", false);
        UUID r1 = UUID.randomUUID(),
                r2 = UUID.randomUUID(),
                review1 = UUID.randomUUID(),
                review2 = UUID.randomUUID();
        Map<String, Object> s1 =
                snapshot(record, project, owner.id, "R1 标题", "R1 目的", "R1 正文", old.id);
        Map<String, Object> s2 =
                snapshot(record, project, owner.id, "R2 标题", "R2 目的", "R2 正文", current.id);
        insertRevision(record, owner.id, reviewer.id, r1, review1, 1, s1, "CHANGES_REQUESTED");
        insertRevision(record, owner.id, reviewer.id, r2, review2, 2, s2, "CHANGES_REQUESTED");
        Map<String, Object> currentValues =
                Map.of("files", List.of(current.id.toString()), "note", "当前值");
        jdbc.update(
                "UPDATE experiment_records SET title='当前工作副本',purpose='当前目的',status='CHANGES_REQUESTED',field_values_json=?,content_json=?,content_html_sanitized='<p>当前正文</p>',content_plain_text='当前正文',current_revision_no=2,current_review_id=?,version=5 WHERE id=?",
                json.writeValueAsString(currentValues),
                json.writeValueAsString(content("当前正文")),
                review2.toString(),
                record.toString());
        return new Fixture(
                owner.token,
                owner.id,
                project,
                record,
                reviewer.id,
                r1,
                r2,
                old.id,
                current.id,
                old.storageKey,
                current.storageKey);
    }

    private Attachment attachment(
            UUID record, UUID uploader, String name, String text, boolean deleted) {
        var stored =
                storage.store(
                        new MockMultipartFile(
                                "file", name, "text/plain", text.getBytes(StandardCharsets.UTF_8)));
        storedKeys.add(stored.storageKey());
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO attachments(id,record_id,uploader_id,original_filename,storage_key,media_type,size_bytes,previewable,created_at,deleted_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                id.toString(),
                record.toString(),
                uploader.toString(),
                name,
                stored.storageKey(),
                stored.mediaType(),
                stored.sizeBytes(),
                stored.previewable(),
                Timestamp.from(Instant.now()),
                deleted ? Timestamp.from(Instant.now()) : null);
        return new Attachment(id, stored.storageKey());
    }

    private Map<String, Object> snapshot(
            UUID record,
            UUID project,
            UUID creator,
            String title,
            String purpose,
            String text,
            UUID attachment) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", record.toString());
        value.put(
                "code",
                jdbc.queryForObject(
                        "SELECT code FROM experiment_records WHERE id=?",
                        String.class,
                        record.toString()));
        value.put("projectId", project.toString());
        value.put("creatorId", creator.toString());
        value.put("title", title);
        value.put("experimentType", "PCR");
        value.put("experimentDate", "2026-07-26");
        value.put("purpose", purpose);
        value.put(
                "templateSnapshot",
                Map.of(
                        "name",
                        "附件模板",
                        "version",
                        1,
                        "fields",
                        List.of(
                                Map.of(
                                        "fieldKey",
                                        "files",
                                        "label",
                                        "附件",
                                        "fieldType",
                                        "FILE",
                                        "required",
                                        false,
                                        "sortOrder",
                                        0),
                                Map.of(
                                        "fieldKey",
                                        "note",
                                        "label",
                                        "说明",
                                        "fieldType",
                                        "MULTI_LINE_TEXT",
                                        "required",
                                        false,
                                        "sortOrder",
                                        1))));
        value.put("fieldValues", Map.of("files", List.of(attachment.toString()), "note", title));
        value.put("contentJson", content(text));
        value.put("contentHtml", "<p>" + text + "</p>");
        value.put("contentPlainText", text);
        return value;
    }

    private Map<String, Object> content(String text) {
        return Map.of(
                "type",
                "doc",
                "content",
                List.of(
                        Map.of(
                                "type",
                                "paragraph",
                                "content",
                                List.of(Map.of("type", "text", "text", text)))));
    }

    private void insertRevision(
            UUID record,
            UUID submitter,
            UUID reviewer,
            UUID revision,
            UUID review,
            int no,
            Map<String, Object> snapshot,
            String status)
            throws Exception {
        String encoded = json.writeValueAsString(snapshot);
        Instant now = Instant.now().plusSeconds(no);
        jdbc.update(
                "INSERT INTO record_revisions(id,record_id,revision_no,snapshot_json,content_hash,submit_note,submitted_by,submitted_at,snapshot_schema_version) VALUES(?,?,?,?,?,?,?,?,1)",
                revision.toString(),
                record.toString(),
                no,
                encoded,
                sha256(encoded),
                "提交 R" + no,
                submitter.toString(),
                Timestamp.from(now));
        UUID attachment =
                UUID.fromString(
                        ((List<?>) ((Map<?, ?>) snapshot.get("fieldValues")).get("files"))
                                .get(0)
                                .toString());
        jdbc.update(
                "INSERT INTO revision_attachments(revision_id,attachment_id,sort_order) VALUES(?,?,0)",
                revision.toString(),
                attachment.toString());
        jdbc.update(
                "INSERT INTO reviews(id,record_id,revision_id,reviewer_id,status,decision_comment,assigned_at,decided_at) VALUES(?,?,?,?,?,?,?,?)",
                review.toString(),
                record.toString(),
                revision.toString(),
                reviewer.toString(),
                status,
                "意见 R" + no,
                Timestamp.from(now),
                Timestamp.from(now.plusSeconds(1)));
    }

    private MvcResult preview(Fixture f, boolean attachments, long version) throws Exception {
        return mvc.perform(
                        post("/api/v1/records/{id}/restore-preview", f.record)
                                .header("Authorization", bearer(f.ownerToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(previewBody(f.r1, version, attachments)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private MvcResult execute(
            Fixture f, boolean attachments, long version, String token, String key, int expected)
            throws Exception {
        return mvc.perform(
                        post("/api/v1/records/{id}/restore", f.record)
                                .header("Authorization", bearer(f.ownerToken))
                                .header("Idempotency-Key", key)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(executeBody(f.r1, version, attachments, token)))
                .andExpect(status().is(expected))
                .andReturn();
    }

    private String previewBody(UUID revision, long version, boolean attachments) throws Exception {
        return json.writeValueAsString(
                Map.of(
                        "sourceRevisionId",
                        revision,
                        "expectedRecordVersion",
                        version,
                        "restoreAttachments",
                        attachments));
    }

    private String executeBody(UUID revision, long version, boolean attachments, String token)
            throws Exception {
        return json.writeValueAsString(
                Map.of(
                        "sourceRevisionId",
                        revision,
                        "expectedRecordVersion",
                        version,
                        "restoreAttachments",
                        attachments,
                        "previewToken",
                        token));
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

    private String createRecord(String token, UUID project, String title) throws Exception {
        return value(
                mvc.perform(
                                post("/api/v1/records")
                                        .header("Authorization", bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"projectId\":\""
                                                        + project
                                                        + "\",\"title\":\""
                                                        + title
                                                        + "\",\"experimentType\":\"PCR\",\"experimentDate\":\"2026-07-26\",\"purpose\":\"当前目的\"}"))
                        .andExpect(status().isOk())
                        .andReturn(),
                "data.id");
    }

    private String value(MvcResult result, String path) throws Exception {
        JsonNode node = json.readTree(result.getResponse().getContentAsByteArray());
        for (String part : path.split("\\.")) node = node.get(part);
        return node.asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String sha256(String v) throws Exception {
        return HexFormat.of()
                .formatHex(
                        MessageDigest.getInstance("SHA-256")
                                .digest(v.getBytes(StandardCharsets.UTF_8)));
    }

    private record User(UUID id, String token) {}

    private record Attachment(UUID id, String storageKey) {}

    private record Fixture(
            String ownerToken,
            UUID owner,
            UUID project,
            UUID record,
            UUID reviewer,
            UUID r1,
            UUID r2,
            UUID oldAttachment,
            UUID currentAttachment,
            String oldStorageKey,
            String currentStorageKey) {}

    @TestConfiguration
    static class FailureConfig {
        @Bean
        AtomicBoolean failRestoreAudit() {
            return new AtomicBoolean(false);
        }

        @Bean
        DomainEventHandler<RecordRevisionRestoredEvent> failingRestoreHandler(AtomicBoolean flag) {
            return new DomainEventHandler<>() {
                public Class<RecordRevisionRestoredEvent> eventType() {
                    return RecordRevisionRestoredEvent.class;
                }

                public void handle(RecordRevisionRestoredEvent event) {
                    if (flag.get())
                        throw new IllegalStateException("intentional restore audit failure");
                }
            };
        }
    }
}
