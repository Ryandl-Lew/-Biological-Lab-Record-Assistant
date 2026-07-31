package com.bionote.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.bionote.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewAttachmentExportIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired ObjectMapper json;

    @BeforeEach
    void clean() {
        jdbc.update("UPDATE experiment_records SET current_review_id=NULL,final_revision_id=NULL");
        jdbc.update("DELETE FROM revision_attachments");
        jdbc.update("DELETE FROM reviews");
        jdbc.update("DELETE FROM record_revisions");
        jdbc.update("DELETE FROM attachments");
        jdbc.update("DELETE FROM experiment_records");
        jdbc.update(
                "DELETE FROM template_fields WHERE template_id IN (SELECT id FROM record_templates WHERE scope='PERSONAL')");
        jdbc.update("DELETE FROM record_templates WHERE scope='PERSONAL'");
        jdbc.update("DELETE FROM audit_events");
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM project_invitations");
        jdbc.update("DELETE FROM project_members");
        jdbc.update("DELETE FROM projects");
        users.deleteAll();
    }

    private String register(String name, String email) throws Exception {
        return value(
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
                        .andReturn(),
                "data.accessToken");
    }

    private String project(String owner) throws Exception {
        return value(
                mvc.perform(
                                post("/api/v1/projects")
                                        .header("Authorization", bearer(owner))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"name\":\"中文实验项目\"}"))
                        .andExpect(status().isOk())
                        .andReturn(),
                "data.id");
    }

    private String invite(String owner, String project, String targetToken, String email)
            throws Exception {
        String invitation =
                value(
                        mvc.perform(
                                        post("/api/v1/projects/" + project + "/invitations")
                                                .header("Authorization", bearer(owner))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"email\":\"" + email + "\"}"))
                                .andExpect(status().isOk())
                                .andReturn(),
                        "data.id");
        mvc.perform(
                        post("/api/v1/invitations/" + invitation + "/accept")
                                .header("Authorization", bearer(targetToken)))
                .andExpect(status().isOk());
        return jdbc.queryForObject(
                "SELECT id FROM users WHERE email_normalized=?", String.class, email);
    }

    private String createRecord(String token, String project, String template) throws Exception {
        return value(
                mvc.perform(
                                post("/api/v1/records")
                                        .header("Authorization", bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"projectId\":\""
                                                        + project
                                                        + "\",\"templateId\":"
                                                        + (template == null
                                                                ? "null"
                                                                : "\"" + template + "\"")
                                                        + ",\"title\":\"中文 PCR 记录一\",\"experimentType\":\"PCR\",\"experimentDate\":\"2026-07-23\",\"purpose\":\"验证中文导出和审核\"}"))
                        .andExpect(status().isOk())
                        .andReturn(),
                "data.id");
    }

    private long version(String token, String record) throws Exception {
        return json.readTree(
                        mvc.perform(
                                        get("/api/v1/records/" + record)
                                                .header("Authorization", bearer(token)))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                .at("/data/version")
                .asLong();
    }

    private void updateRequired(String token, String record, long version, String title)
            throws Exception {
        mvc.perform(
                        put("/api/v1/records/" + record)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"version\":"
                                                + version
                                                + ",\"title\":\""
                                                + title
                                                + "\",\"experimentType\":\"PCR\",\"experimentDate\":\"2026-07-23\",\"purpose\":\"验证中文导出和审核\",\"fieldValues\":{\"sample_code\":\"TEST-01\",\"template_source\":\"基因组 DNA\",\"target_locus\":\"BRCA1 exon 11\",\"primer_pair\":\"F/R primer 各 0.4 μM\",\"polymerase_mix\":\"高保真 PCR Mix\",\"reaction_volume\":25,\"reaction_system\":\"2× Mix 12.5 μL，模板 1 μL\",\"annealing_temperature\":60,\"cycle_count\":35,\"expected_product_size\":850,\"control_design\":\"阳性对照和 NTC\",\"amplification_result\":\"目标条带单一且清晰\"},\"contentJson\":{\"type\":\"doc\"},\"contentHtml\":\"<h2>实验结果</h2><p>扩增成功，中文正文。</p>\"}"))
                .andExpect(status().isOk());
    }

    private void updateQpcr(
            String token, String record, long version, String title, String attachment)
            throws Exception {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("sample_group", "Normoxia 与 Hypoxia-12h");
        fields.put("extraction_summary", "柱式提取并进行 DNase 处理");
        fields.put("rna_concentration", 286.4);
        fields.put("a260_280", 1.96);
        fields.put("target_gene", "VEGFA");
        fields.put("reference_gene", "GAPDH");
        fields.put("primer_info", "VEGFA/GAPDH 引物终浓度 0.2 μM");
        fields.put("qpcr_mix", "SYBR Green qPCR Mix");
        fields.put("reaction_volume", 10);
        fields.put("replicate_design", "3 个生物学重复和 3 个技术重复");
        fields.put("cycling_program", "95℃ 30 s；40 cycles");
        fields.put("ct_summary", "目标 Ct 24–30，内参 Ct 19–20");
        fields.put("melt_curve_result", "存在肩峰");
        fields.put("relative_expression", 3.42);
        fields.put("result_conclusion", "异常复孔已复核，补充引物特异性验证结果");
        fields.put("raw_data_file", java.util.List.of(attachment));
        Map<String, Object> body =
                Map.of(
                        "version",
                        version,
                        "title",
                        title,
                        "experimentType",
                        "qPCR",
                        "experimentDate",
                        "2026-07-23",
                        "purpose",
                        "验证删除旧原始数据附件后仍可再次提交审核",
                        "fieldValues",
                        fields,
                        "contentJson",
                        Map.of("type", "doc"),
                        "contentHtml",
                        "<p>已完成退回后的修改。</p>");
        mvc.perform(
                        put("/api/v1/records/" + record)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    private String upload(String token, String record, String name, String type, byte[] bytes)
            throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", name, type, bytes);
        return value(
                mvc.perform(
                                multipart("/api/v1/records/" + record + "/attachments")
                                        .file(file)
                                        .header("Authorization", bearer(token)))
                        .andExpect(status().isOk())
                        .andReturn(),
                "data.id");
    }

    private String value(MvcResult result, String path) throws Exception {
        JsonNode n = json.readTree(result.getResponse().getContentAsString());
        for (String part : path.split("\\.")) n = n.get(part);
        return n.asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private byte[] png() {
        return new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0};
    }

    @Test
    void attachmentValidationPreviewAndAuthorizationAreEnforced() throws Exception {
        String owner = register("负责人", "owner@example.com"),
                creator = register("创建者", "creator@example.com"),
                outsider = register("外部", "outside@example.com"),
                p = project(owner);
        invite(owner, p, creator, "creator@example.com");
        String record = createRecord(creator, p, null),
                attachment = upload(creator, record, "图像.png", "image/png", png());
        mvc.perform(
                        get("/api/v1/attachments/" + attachment + "/preview")
                                .header("Authorization", bearer(creator)))
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                        "Content-Disposition",
                                        org.hamcrest.Matchers.startsWith("inline")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().contentType("image/png"));
        MockMultipartFile markdownFile =
                new MockMultipartFile(
                        "file",
                        "实验结果.md",
                        "text/markdown",
                        "# 实验结果\n\n- 扩增成功".getBytes(StandardCharsets.UTF_8));
        MvcResult markdownUpload =
                mvc.perform(
                                multipart("/api/v1/records/" + record + "/attachments")
                                        .file(markdownFile)
                                        .header("Authorization", bearer(creator)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.previewable").value(true))
                        .andReturn();
        String markdown = value(markdownUpload, "data.id");
        mvc.perform(
                        get("/api/v1/attachments/" + markdown + "/preview")
                                .header("Authorization", bearer(creator)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/markdown"))
                .andExpect(content().encoding(StandardCharsets.UTF_8))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("扩增成功")));
        mvc.perform(
                        get("/api/v1/attachments/" + attachment + "/download")
                                .header("Authorization", bearer(outsider)))
                .andExpect(status().isNotFound());
        MockMultipartFile fake =
                new MockMultipartFile(
                        "file", "报告.png.exe", "application/octet-stream", new byte[] {1, 2});
        mvc.perform(
                        multipart("/api/v1/records/" + record + "/attachments")
                                .file(fake)
                                .header("Authorization", bearer(creator)))
                .andExpect(status().isBadRequest());
        MockMultipartFile empty = new MockMultipartFile("file", "空.txt", "text/plain", new byte[0]);
        mvc.perform(
                        multipart("/api/v1/records/" + record + "/attachments")
                                .file(empty)
                                .header("Authorization", bearer(creator)))
                .andExpect(status().isBadRequest());
        String docx =
                upload(
                        creator,
                        record,
                        "数据.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        new byte[] {0x50, 0x4b, 0x03, 0x04, 0, 0});
        mvc.perform(
                        get("/api/v1/attachments/" + docx + "/preview")
                                .header("Authorization", bearer(creator)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("PREVIEW_NOT_SUPPORTED"));
        mvc.perform(
                        get("/api/v1/attachments/" + docx + "/download")
                                .header("Authorization", bearer(creator)))
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                        "Content-Disposition",
                                        org.hamcrest.Matchers.startsWith("attachment")));
    }

    @Test
    void r1ReturnR2ApproveAndExportsUseImmutableFinalSnapshot() throws Exception {
        String owner = register("负责人", "owner@example.com"),
                creator = register("创建者", "creator@example.com"),
                reviewer = register("审核人", "reviewer@example.com"),
                p = project(owner);
        invite(owner, p, creator, "creator@example.com");
        String reviewerId = invite(owner, p, reviewer, "reviewer@example.com");
        mvc.perform(
                        patch("/api/v1/projects/" + p + "/members/" + reviewerId + "/role")
                                .header("Authorization", bearer(owner))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"role\":\"REVIEWER\"}"))
                .andExpect(status().isOk());
        String record = createRecord(creator, p, "10000000-0000-0000-0000-000000000001");
        updateRequired(creator, record, 0, "中文 PCR 记录一");
        String oldAttachment = upload(creator, record, "结果.png", "image/png", png());
        long v1 = version(creator, record);
        MvcResult submitted =
                mvc.perform(
                                post("/api/v1/records/" + record + "/submissions")
                                        .header("Authorization", bearer(creator))
                                        .header("Idempotency-Key", "submit-r1")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"reviewerId\":\""
                                                        + reviewerId
                                                        + "\",\"submitNote\":\"请审核\",\"expectedRecordVersion\":"
                                                        + v1
                                                        + "}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.revisionNo").value(1))
                        .andReturn();
        String review1 = value(submitted, "data.review.id"),
                revision1 = value(submitted, "data.id");
        mvc.perform(
                        post("/api/v1/records/" + record + "/submissions")
                                .header("Authorization", bearer(creator))
                                .header("Idempotency-Key", "submit-r1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"reviewerId\":\""
                                                + reviewerId
                                                + "\",\"expectedRecordVersion\":"
                                                + v1
                                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(revision1));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM record_revisions WHERE record_id=?",
                                Integer.class,
                                record))
                .isEqualTo(1);
        mvc.perform(
                        multipart("/api/v1/records/" + record + "/attachments")
                                .file(new MockMultipartFile("file", "锁定.png", "image/png", png()))
                                .header("Authorization", bearer(creator)))
                .andExpect(status().isConflict());
        mvc.perform(
                        post("/api/v1/records/"
                                        + record
                                        + "/reviews/"
                                        + review1
                                        + "/request-changes")
                                .header("Authorization", bearer(reviewer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"comment\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/api/v1/records/"
                                        + record
                                        + "/reviews/"
                                        + review1
                                        + "/request-changes")
                                .header("Authorization", bearer(reviewer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"comment\":\"请补充重复实验\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.review.status").value("CHANGES_REQUESTED"));
        mvc.perform(
                        post("/api/v1/records/" + record + "/reviews/" + review1 + "/approve")
                                .header("Authorization", bearer(reviewer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isConflict());
        mvc.perform(
                        delete("/api/v1/attachments/" + oldAttachment)
                                .header("Authorization", bearer(creator)))
                .andExpect(status().isNoContent());
        mvc.perform(
                        get("/api/v1/attachments/" + oldAttachment + "/preview")
                                .header("Authorization", bearer(creator)))
                .andExpect(status().isOk());
        long v3 = version(creator, record);
        updateRequired(creator, record, v3, "中文 PCR 记录二");
        long v4 = version(creator, record);
        MvcResult submitted2 =
                mvc.perform(
                                post("/api/v1/records/" + record + "/submissions")
                                        .header("Authorization", bearer(creator))
                                        .header("Idempotency-Key", "submit-r2")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"reviewerId\":\""
                                                        + reviewerId
                                                        + "\",\"expectedRecordVersion\":"
                                                        + v4
                                                        + "}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.revisionNo").value(2))
                        .andReturn();
        String review2 = value(submitted2, "data.review.id");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT snapshot_json FROM record_revisions WHERE id=?",
                                String.class,
                                revision1))
                .contains("中文 PCR 记录一")
                .doesNotContain("中文 PCR 记录二");
        mvc.perform(
                        post("/api/v1/records/" + record + "/reviews/" + review2 + "/approve")
                                .header("Authorization", bearer(reviewer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"comment\":\"数据完整\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.review.status").value("APPROVED"));
        mvc.perform(
                        get("/api/v1/records/" + record + "/exports/preview")
                                .header("Authorization", bearer(creator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.report.title").value("中文 PCR 记录二"))
                .andExpect(jsonPath("$.data.report.revisionNo").value(2));
        MvcResult md =
                mvc.perform(
                                get("/api/v1/records/" + record + "/exports/markdown")
                                        .header("Authorization", bearer(creator)))
                        .andExpect(status().isOk())
                        .andReturn();
        assertThat(md.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("中文 PCR 记录二", "R2", "数据完整");
        byte[] pdf =
                mvc.perform(
                                get("/api/v1/records/" + record + "/exports/pdf")
                                        .header("Authorization", bearer(creator)))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType("application/pdf"))
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        Path sample = Path.of("..", "output", "pdf", "bionote-mvp-sample.pdf");
        Files.createDirectories(sample.getParent());
        Files.write(sample, pdf);
        try (var document = Loader.loadPDF(pdf)) {
            assertThat(new PDFTextStripper().getText(document)).contains("PCR", "数据完整");
        }
    }

    @Test
    void reviewerDemotionRequiresTransactionalReassignment() throws Exception {
        String owner = register("负责人", "owner@example.com"),
                creator = register("创建者", "creator@example.com"),
                reviewer = register("审核人", "reviewer@example.com"),
                p = project(owner);
        String ownerId =
                jdbc.queryForObject(
                        "SELECT id FROM users WHERE email_normalized='owner@example.com'",
                        String.class);
        invite(owner, p, creator, "creator@example.com");
        String reviewerId = invite(owner, p, reviewer, "reviewer@example.com");
        mvc.perform(
                        patch("/api/v1/projects/" + p + "/members/" + reviewerId + "/role")
                                .header("Authorization", bearer(owner))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"role\":\"REVIEWER\"}"))
                .andExpect(status().isOk());
        String record = createRecord(creator, p, null);
        MvcResult submission =
                mvc.perform(
                                post("/api/v1/records/" + record + "/submissions")
                                        .header("Authorization", bearer(creator))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"reviewerId\":\""
                                                        + reviewerId
                                                        + "\",\"expectedRecordVersion\":0}"))
                        .andExpect(status().isOk())
                        .andReturn();
        String review = value(submission, "data.review.id");
        mvc.perform(
                        patch("/api/v1/projects/" + p + "/members/" + reviewerId + "/role")
                                .header("Authorization", bearer(owner))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_REASSIGNMENT_REQUIRED"));
        mvc.perform(
                        patch("/api/v1/projects/" + p + "/members/" + reviewerId + "/role")
                                .header("Authorization", bearer(owner))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"role\":\"MEMBER\",\"reassignments\":{\""
                                                + review
                                                + "\":\""
                                                + ownerId
                                                + "\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("MEMBER"));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT reviewer_id FROM reviews WHERE id=?", String.class, review))
                .isEqualTo(ownerId);
    }

    @Test
    void staleOptionalFileReferenceIsRemovedBeforeResubmission() throws Exception {
        String owner = register("负责人", "owner@example.com"),
                creator = register("创建者", "creator@example.com"),
                reviewer = register("审核人", "reviewer@example.com"),
                p = project(owner);
        invite(owner, p, creator, "creator@example.com");
        String reviewerId = invite(owner, p, reviewer, "reviewer@example.com");
        mvc.perform(
                        patch("/api/v1/projects/" + p + "/members/" + reviewerId + "/role")
                                .header("Authorization", bearer(owner))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"role\":\"REVIEWER\"}"))
                .andExpect(status().isOk());
        String record = createRecord(creator, p, "10000000-0000-0000-0000-000000000002"),
                raw =
                        upload(
                                creator,
                                record,
                                "VEGFA-Ct.csv",
                                "text/csv",
                                "sample,ct\nH12-2,29.84\n".getBytes(StandardCharsets.UTF_8));
        updateQpcr(creator, record, 0, "HepG2 缺氧 12 h VEGFA 表达分析", raw);
        long v1 = version(creator, record);
        MvcResult first =
                mvc.perform(
                                post("/api/v1/records/" + record + "/submissions")
                                        .header("Authorization", bearer(creator))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"reviewerId\":\""
                                                        + reviewerId
                                                        + "\",\"expectedRecordVersion\":"
                                                        + v1
                                                        + "}"))
                        .andExpect(status().isOk())
                        .andReturn();
        String review = value(first, "data.review.id");
        mvc.perform(
                        post("/api/v1/records/"
                                        + record
                                        + "/reviews/"
                                        + review
                                        + "/request-changes")
                                .header("Authorization", bearer(reviewer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"comment\":\"请替换原始数据并补充说明\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/attachments/" + raw).header("Authorization", bearer(creator)))
                .andExpect(status().isNoContent());
        long changedVersion = version(creator, record);
        updateQpcr(creator, record, changedVersion, "HepG2 缺氧 12 h VEGFA 表达分析（已修改）", raw);
        long v2 = version(creator, record);
        mvc.perform(
                        post("/api/v1/records/" + record + "/submissions")
                                .header("Authorization", bearer(creator))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"reviewerId\":\""
                                                + reviewerId
                                                + "\",\"expectedRecordVersion\":"
                                                + v2
                                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revisionNo").value(2));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT field_values_json FROM experiment_records WHERE id=?",
                                String.class,
                                record))
                .doesNotContain(raw);
    }
}
