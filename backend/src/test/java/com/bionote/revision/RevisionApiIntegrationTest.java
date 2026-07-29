package com.bionote.revision;

import com.bionote.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RevisionApiIntegrationTest {
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired UserRepository users; @Autowired ObjectMapper json;

    @BeforeEach void clean() {
        jdbc.update("UPDATE experiment_records SET current_review_id=NULL,final_revision_id=NULL");
        jdbc.update("DELETE FROM revision_attachments"); jdbc.update("DELETE FROM reviews"); jdbc.update("DELETE FROM record_revisions");
        jdbc.update("DELETE FROM attachments"); jdbc.update("DELETE FROM experiment_records"); jdbc.update("DELETE FROM audit_events");
        jdbc.update("DELETE FROM notifications"); jdbc.update("DELETE FROM project_invitations"); jdbc.update("DELETE FROM project_members"); jdbc.update("DELETE FROM projects"); users.deleteAll();
    }

    @Test void thirtyRevisionsArePagedDescendingWithoutSnapshotsAndFlagsAreCorrect() throws Exception {
        Fixture fixture = fixture("owner@example.com"); List<Ids> revisions = new ArrayList<>();
        for (int i = 1; i <= 30; i++) revisions.add(insertRevision(fixture, i, "标题 " + i, "目的 " + i));
        Ids current = revisions.get(29), finalRevision = revisions.get(28);
        jdbc.update("UPDATE experiment_records SET status='IN_REVIEW',current_revision_no=30,current_review_id=?,final_revision_id=? WHERE id=?",
                current.reviewId.toString(), finalRevision.revisionId.toString(), fixture.recordId.toString());

        mvc.perform(get("/api/v1/records/{id}/revisions", fixture.recordId).header("Authorization", bearer(fixture.ownerToken)).param("page", "0").param("size", "20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.meta.totalElements").value(30))
                .andExpect(jsonPath("$.data.length()").value(20)).andExpect(jsonPath("$.data[0].revisionNo").value(30))
                .andExpect(jsonPath("$.data[0].current").value(true)).andExpect(jsonPath("$.data[0].snapshot").doesNotExist())
                .andExpect(jsonPath("$.data[0].attachments").doesNotExist());
        mvc.perform(get("/api/v1/records/{id}/revisions", fixture.recordId).header("Authorization", bearer(fixture.ownerToken)).param("page", "1").param("size", "20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(10)).andExpect(jsonPath("$.data[0].revisionNo").value(10));
        mvc.perform(get("/api/v1/records/{recordId}/revisions/{revisionId}", fixture.recordId, finalRevision.revisionId).header("Authorization", bearer(fixture.ownerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.finalRevision").value(true))
                .andExpect(jsonPath("$.data.snapshot.title").value("标题 29"))
                .andExpect(jsonPath("$.data.snapshot.contentHtml").doesNotExist());
    }

    @Test void outsiderAndMismatchedRecordCannotDiscoverRevision() throws Exception {
        Fixture fixture = fixture("owner@example.com"); Ids revision = insertRevision(fixture, 1, "R1", "目的");
        String outsider = register("外部用户", "outsider@example.com");
        String otherRecord = createRecord(fixture.ownerToken, fixture.projectId.toString(), "其他记录");
        mvc.perform(get("/api/v1/records/{id}/revisions", fixture.recordId).header("Authorization", bearer(outsider))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/records/{recordId}/revisions/{revisionId}", otherRecord, revision.revisionId).header("Authorization", bearer(fixture.ownerToken)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REVISION_NOT_FOUND"));
    }

    @Test void pendingReviewSummaryPreservesReviewerDecisionCapability() throws Exception {
        Fixture fixture = fixture("owner@example.com"); Ids revision = insertRevision(fixture, 1, "R1", "purpose");
        jdbc.update("UPDATE reviews SET status='PENDING',decision_comment=NULL,decided_at=NULL WHERE id=?", revision.reviewId.toString());

        mvc.perform(get("/api/v1/records/{id}/revisions", fixture.recordId)
                        .header("Authorization", bearer(fixture.reviewerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].review.canDecide").value(true));
        mvc.perform(get("/api/v1/records/{id}/revisions", fixture.recordId)
                        .header("Authorization", bearer(fixture.ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].review.canDecide").value(false));
        mvc.perform(get("/api/v1/records/{recordId}/revisions/{revisionId}", fixture.recordId, revision.revisionId)
                        .header("Authorization", bearer(fixture.reviewerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.review.canDecide").value(true));
    }

    @Test void revisionDiffIsStructuredAndWorkingCopyCarriesVersion() throws Exception {
        Fixture fixture = fixture("owner@example.com"); Ids r1 = insertRevision(fixture, 1, "旧标题", "旧目的"); Ids r2 = insertRevision(fixture, 2, "新标题", "新目的");
        jdbc.update("UPDATE experiment_records SET title='工作副本标题',purpose='工作副本目的',version=7,current_revision_no=2 WHERE id=?", fixture.recordId.toString());
        mvc.perform(get("/api/v1/records/{recordId}/revision-diff", fixture.recordId).header("Authorization", bearer(fixture.ownerToken))
                        .param("fromRevisionId", r1.revisionId.toString()).param("toRevisionId", r2.revisionId.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sections[?(@.key == 'fixed:title')].status").value("MODIFIED"))
                .andExpect(jsonPath("$.data.sections[?(@.kind == 'REVIEW_METADATA')]").exists())
                .andExpect(jsonPath("$.data.sections[0].before").exists());
        mvc.perform(get("/api/v1/records/{recordId}/revision-diff", fixture.recordId).header("Authorization", bearer(fixture.ownerToken))
                        .param("fromRevisionId", r1.revisionId.toString()).param("to", "WORKING_COPY"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.to.type").value("WORKING_COPY"))
                .andExpect(jsonPath("$.data.to.recordVersion").value(7))
                .andExpect(jsonPath("$.data.sections[?(@.key == 'fixed:title')].after").value("工作副本标题"));
    }

    private Fixture fixture(String ownerEmail) throws Exception {
        String ownerToken = register("负责人", ownerEmail); UUID ownerId = UUID.fromString(jdbc.queryForObject("SELECT id FROM users WHERE email_normalized=?", String.class, ownerEmail));
        UUID projectId = UUID.fromString(value(mvc.perform(post("/api/v1/projects").header("Authorization", bearer(ownerToken)).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Revision 项目\"}")).andExpect(status().isOk()).andReturn(), "data.id"));
        UUID recordId = UUID.fromString(createRecord(ownerToken, projectId.toString(), "工作副本"));
        String reviewerToken = register("审核者", "reviewer-" + UUID.randomUUID() + "@example.com");
        UUID reviewerId = UUID.fromString(jdbc.queryForObject("SELECT id FROM users WHERE email_normalized LIKE 'reviewer-%' ORDER BY created_at DESC LIMIT 1", String.class));
        jdbc.update("INSERT INTO project_members(project_id,user_id,role,joined_at) VALUES(?,?, 'REVIEWER',?)", projectId.toString(), reviewerId.toString(), Timestamp.from(Instant.now()));
        return new Fixture(ownerToken, ownerId, projectId, recordId, reviewerToken, reviewerId);
    }

    private Ids insertRevision(Fixture fixture, int no, String title, String purpose) throws Exception {
        UUID revision = UUID.randomUUID(), review = UUID.randomUUID(); Instant now = Instant.now().plusSeconds(no);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", fixture.recordId.toString()); snapshot.put("code", jdbc.queryForObject("SELECT code FROM experiment_records WHERE id=?", String.class, fixture.recordId.toString()));
        snapshot.put("projectId", fixture.projectId.toString()); snapshot.put("creatorId", fixture.ownerId.toString());
        snapshot.put("title", title); snapshot.put("experimentType", "PCR"); snapshot.put("experimentDate", "2026-07-26"); snapshot.put("purpose", purpose);
        snapshot.put("templateSnapshot", Map.of("name", "模板", "version", 1, "fields", List.of(Map.of("fieldKey", "note", "label", "说明", "fieldType", "MULTI_LINE_TEXT", "required", false, "sortOrder", 0))));
        snapshot.put("fieldValues", Map.of("note", "说明 " + no));
        snapshot.put("contentJson", Map.of("type", "doc", "content", List.of(Map.of("type", "paragraph", "content", List.of(Map.of("type", "text", "text", "正文 " + no))))));
        snapshot.put("contentHtml", "<p>正文 " + no + "</p>"); snapshot.put("contentPlainText", "正文 " + no);
        jdbc.update("INSERT INTO record_revisions(id,record_id,revision_no,snapshot_json,content_hash,submit_note,submitted_by,submitted_at,snapshot_schema_version) VALUES(?,?,?,?,?,?,?,?,1)",
                revision.toString(), fixture.recordId.toString(), no, json.writeValueAsString(snapshot), "a".repeat(63) + (no % 10), "提交 " + no, fixture.ownerId.toString(), Timestamp.from(now));
        jdbc.update("INSERT INTO reviews(id,record_id,revision_id,reviewer_id,status,decision_comment,assigned_at,decided_at) VALUES(?,?,?,?,?,?,?,?)",
                review.toString(), fixture.recordId.toString(), revision.toString(), fixture.reviewerId.toString(), no % 2 == 0 ? "APPROVED" : "CHANGES_REQUESTED", "意见 " + no, Timestamp.from(now), Timestamp.from(now.plusSeconds(1)));
        return new Ids(revision, review);
    }

    private String register(String name, String email) throws Exception { return value(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"Password123!\"}")).andExpect(status().isOk()).andReturn(), "data.accessToken"); }
    private String createRecord(String token, String project, String title) throws Exception { return value(mvc.perform(post("/api/v1/records").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{\"projectId\":\"" + project + "\",\"title\":\"" + title + "\",\"experimentType\":\"PCR\",\"experimentDate\":\"2026-07-26\",\"purpose\":\"目的\"}")).andExpect(status().isOk()).andReturn(), "data.id"); }
    private String value(MvcResult result, String path) throws Exception { JsonNode node = json.readTree(result.getResponse().getContentAsString()); for (String part : path.split("\\.")) node = node.get(part); return node.asText(); }
    private String bearer(String token) { return "Bearer " + token; }
    private record Fixture(String ownerToken, UUID ownerId, UUID projectId, UUID recordId, String reviewerToken, UUID reviewerId) {}
    private record Ids(UUID revisionId, UUID reviewId) {}
}
