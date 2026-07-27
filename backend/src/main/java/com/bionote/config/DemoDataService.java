package com.bionote.config;

import com.bionote.attachment.AttachmentService;
import com.bionote.auth.AuthDtos;
import com.bionote.auth.AuthService;
import com.bionote.project.ProjectDtos;
import com.bionote.project.ProjectService;
import com.bionote.record.RecordDtos;
import com.bionote.record.RecordService;
import com.bionote.review.ReviewDtos;
import com.bionote.review.ReviewService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DemoDataService {
    private static final UUID PCR_TEMPLATE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID QPCR_TEMPLATE = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID CELL_TEMPLATE = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final Account[] ACCOUNTS = {
            new Account("林知远（项目负责人）", "owner@example.com"),
            new Account("周明希（实验成员）", "member@example.com"),
            new Account("陈若宁（审核教师）", "reviewer@example.com"),
            new Account("演示外部用户", "outsider@example.com")
    };

    private final AuthService authService;
    private final ProjectService projectService;
    private final RecordService recordService;
    private final ReviewService reviewService;
    private final AttachmentService attachmentService;
    private final JdbcTemplate jdbc;
    private final String password;

    public DemoDataService(AuthService authService, ProjectService projectService, RecordService recordService,
                           ReviewService reviewService, AttachmentService attachmentService, JdbcTemplate jdbc,
                           @Value("${bionote.dev-seed-password:Demo123!}") String password) {
        this.authService = authService;
        this.projectService = projectService;
        this.recordService = recordService;
        this.reviewService = reviewService;
        this.attachmentService = attachmentService;
        this.jdbc = jdbc;
        this.password = password;
    }

    public void seed() {
        for (Account account : ACCOUNTS) ensureAccount(account);
        UUID owner = userId("owner@example.com");
        UUID member = userId("member@example.com");
        UUID reviewer = userId("reviewer@example.com");
        seedPcrOptimizationProject(owner, member, reviewer);
        seedQpcrProject(member, owner, reviewer);
        seedCellCultureProject(owner, member, reviewer);
        seedArchivedCourseProject(owner, member, reviewer);
        enhancePhase2RevisionDemo(reviewer);
    }

    private void enhancePhase2RevisionDemo(UUID reviewer){List<String> records=jdbc.queryForList("SELECT id FROM experiment_records WHERE title LIKE ? AND current_revision_no=1 AND status='CHANGES_REQUESTED'",String.class,"HepG2 缺氧 12 h VEGFA 表达分析%");if(records.isEmpty())return;UUID recordId=UUID.fromString(records.get(0));UUID creator=UUID.fromString(jdbc.queryForObject("SELECT creator_id FROM experiment_records WHERE id=?",String.class,recordId.toString()));RecordDtos.View changed=recordService.get(creator,recordId);Map<String,Object> revisedValues=new LinkedHashMap<>(changed.fieldValues());revisedValues.put("relative_expression",3.42);revisedValues.put("melt_curve_result","存在多峰或杂峰");Integer supplement=jdbc.queryForObject("SELECT COUNT(*) FROM attachments WHERE record_id=? AND original_filename=? AND deleted_at IS NULL",Integer.class,recordId.toString(),"VEGFA-R2-扩增曲线复核.md");if(supplement!=null&&supplement==0)attachmentService.upload(creator,recordId,markdown("VEGFA-R2-扩增曲线复核.md","# VEGFA R2 扩增曲线复核\n\n- H12-2 A9 复孔偏离 0.71 Ct\n- 熔解曲线肩峰仍可见\n- 建议补充引物特异性与异常孔剔除标准\n").file());RecordDtos.View revised=recordService.update(creator,recordId,new RecordDtos.UpdateRequest(changed.version(),changed.title()+"（R2 复核）",changed.experimentType(),changed.experimentDate(),changed.purpose()+" 已根据 R1 审核意见补充异常孔复核。",revisedValues,changed.contentJson(),changed.contentHtml()+"<h2>R1 退回后补充</h2><p>已核对扩增曲线和异常复孔，仍需补做引物特异性验证。</p>"));ReviewDtos.RevisionView r2=reviewService.submit(creator,recordId,new ReviewDtos.SubmitRequest(reviewer,"已按 R1 意见补充，提交 R2",revised.version()),"seed:"+recordId+":r2");reviewService.requestChanges(reviewer,recordId,r2.review().id(),"R2 已补充曲线复核，但仍需给出引物特异性验证结果后再批准。");}

    private void seedPcrOptimizationProject(UUID owner, UUID member, UUID reviewer) {
        String name = "BRCA1 外显子 PCR 条件优化";
        if (projectExists(name)) return;
        ProjectDtos.ProjectView project = createProject(owner, member, reviewer, name,
                "优化 BRCA1 exon 11 扩增特异性并建立家系样本复核条件。",
                "围绕 BRCA1 exon 11 的高 GC 区域开展退火温度、模板量和循环参数优化。项目包含梯度 PCR、家系样本复核、阴性对照污染排查及凝胶结果判读，结论以独立重复和对照结果为依据。");

        RecordDtos.View optimized = createRecord(member, project.id(), PCR_TEMPLATE,
                "BRCA1 exon 11 退火温度梯度优化", "PCR", LocalDate.of(2026, 7, 8),
                "比较 58–64℃ 梯度条件下 850 bp 目标片段的扩增特异性，确定家系样本检测参数。",
                pcrValues("BRCA1-GRAD-01", "基因组 DNA", "BRCA1 exon 11", 61.5, 850, "目标条带单一且清晰"),
                """
                <h2>实验设计</h2><p>采用同一份健康对照基因组 DNA，在 58、59.5、61.5、63 和 64℃ 条件下进行梯度 PCR，每个温度设置无模板对照。</p>
                <h2>观察结果</h2><ul><li>58–59.5℃ 可见约 500 bp 非特异条带。</li><li>61.5℃ 目标条带最清晰，背景最低。</li><li>63℃ 目标条带强度下降，64℃ 接近扩增下限。</li></ul>
                <h2>结论</h2><p>后续样本采用 61.5℃ 退火、35 个循环，并保留无模板对照。</p>
                """,
                List.of(markdown("BRCA1-PCR-梯度与电泳判读.md", """
                        # BRCA1 exon 11 梯度 PCR 判读

                        - 58℃：目标条带强，存在明显非特异扩增
                        - 59.5℃：非特异条带减弱
                        - 61.5℃：850 bp 目标条带单一、清晰
                        - 63℃：目标条带偏弱
                        - 64℃：扩增量不足

                        建议工作条件：61.5℃，35 cycles。
                        """)));
        transition(member, reviewer, optimized, Outcome.COMPLETED_R2,
                "请补充无模板对照结果，并说明为何不选择 59.5℃。", "已补充 NTC 判读和温度选择依据，复核通过。");

        RecordDtos.View familySamples = createRecord(member, project.id(), PCR_TEMPLATE,
                "BRCA1 家系样本扩增复核", "PCR", LocalDate.of(2026, 7, 18),
                "使用优化后的 61.5℃ 条件对先证者及双亲样本独立扩增，为 Sanger 测序准备产物。",
                pcrValues("BRCA1-FAM-02", "基因组 DNA", "BRCA1 exon 11", 61.5, 850, "目标条带单一且清晰"),
                "<h2>样本与对照</h2><p>P01、F01、M01 各设置一个反应；使用已验证野生型样本作为阳性对照，并设置 NTC。</p><h2>结果</h2><p>三份家系样本均获得单一 850 bp 条带，NTC 无可见扩增，已留取产物待纯化。</p>",
                List.of());
        transition(member, reviewer, familySamples, Outcome.IN_REVIEW, null, null);

        createRecord(owner, project.id(), null,
                "NTC 污染排查与工作台清洁验证", "PCR 质量控制", LocalDate.of(2026, 7, 22),
                "排查上一批次无模板对照出现弱条带的潜在污染来源，并验证分区清洁措施。",
                Map.of(),
                "<h2>排查范围</h2><p>分别检测水、引物工作液、预混液和加样区表面擦拭液；更换滤芯枪头并使用 DNA 去除剂清洁。</p><h2>当前进度</h2><p>已完成清洁和试剂分装，等待下一轮空白对照扩增。</p>",
                List.of());
        spreadTimeline(project.id(), LocalDate.of(2026, 6, 24));
    }

    private void seedQpcrProject(UUID owner, UUID member, UUID reviewer) {
        String name = "HepG2 缺氧响应 qPCR";
        if (projectExists(name)) return;
        ProjectDtos.ProjectView project = createProject(owner, member, reviewer, name,
                "比较 HepG2 细胞在不同缺氧时长下 HIF1A/VEGFA 转录响应。",
                "HepG2 细胞在 1% O₂ 条件下处理 0、6 和 12 小时。使用 GAPDH 作为内参，采用 SYBR Green 方法和 2^-ΔΔCt 计算相对表达量。每组 3 个生物学重复，每份 cDNA 3 个技术重复，并保留 NTC 与 -RT 对照。");

        RecordDtos.View hif1a = createRecord(member, project.id(), QPCR_TEMPLATE,
                "HepG2 缺氧 6 h HIF1A 表达分析", "qPCR", LocalDate.of(2026, 7, 3),
                "定量评估 6 小时缺氧处理后 HIF1A mRNA 的相对表达变化，并检查重复与熔解曲线质量。",
                qpcrValues("Normoxia-0h: N1–N3；Hypoxia-6h: H6-1–H6-3", "HIF1A", "GAPDH", 2.34,
                        "单一特异峰", "HIF1A 在缺氧 6 h 后上调约 2.34 倍；技术重复 SD 均小于 0.25 Ct。"),
                "<h2>质量控制</h2><p>RNA A260/A280 为 1.96，-RT 与 NTC 均未检出有效扩增，目标基因和内参熔解曲线均为单峰。</p><h2>统计结果</h2><p>缺氧 6 h 组 HIF1A 相对表达量为 2.34 ± 0.31。</p><h2>结论</h2><p>6 h 缺氧已经触发可重复的 HIF1A 转录响应。</p>",
                List.of(csv("HIF1A-6h-Ct原始数据.csv", "sample,group,target_ct,reference_ct\nN1,Normoxia,25.81,19.42\nN2,Normoxia,25.66,19.35\nN3,Normoxia,25.92,19.51\nH6-1,Hypoxia6h,24.22,19.47\nH6-2,Hypoxia6h,24.08,19.39\nH6-3,Hypoxia6h,24.31,19.55\n", "raw_data_file")));
        transition(member, reviewer, hif1a, Outcome.COMPLETED_R1, null, "重复一致性和阴性对照符合要求，结论可信。");

        RecordDtos.View vegfa = createRecord(member, project.id(), QPCR_TEMPLATE,
                "HepG2 缺氧 12 h VEGFA 表达分析", "qPCR", LocalDate.of(2026, 7, 12),
                "检测 12 小时缺氧处理对 VEGFA 转录水平的影响，并评估一个高 Ct 技术重复是否需要剔除。",
                qpcrValues("Normoxia-0h: N1–N3；Hypoxia-12h: H12-1–H12-3", "VEGFA", "GAPDH", 3.87,
                        "存在肩峰", "H12-2 的一个技术重复偏离 0.71 Ct，且目标基因熔解曲线出现轻微肩峰，需复核。"),
                "<h2>异常说明</h2><p>H12-2 第三个复孔 Ct=29.84，较其余两个复孔高 0.71 Ct；熔解曲线主峰旁存在轻微肩峰。</p><h2>初步结果</h2><p>未剔除异常孔时 VEGFA 相对表达约为 3.87 倍。</p>",
                List.of(csv("VEGFA-12h-Ct待复核.csv", "sample,well,target_ct,reference_ct\nH12-2,A7,29.13,19.88\nH12-2,A8,29.18,19.91\nH12-2,A9,29.84,19.90\n", "raw_data_file")));
        transition(member, reviewer, vegfa, Outcome.CHANGES_REQUESTED_R2,
                "请核对 H12-2 异常复孔的扩增曲线，并补充预先定义的异常孔剔除标准。", null);

        createRecord(owner, project.id(), null,
                "HepG2 RNA 完整性与浓度复测", "RNA 质量控制", LocalDate.of(2026, 7, 20),
                "复测 12 h 缺氧组 RNA 浓度与纯度，判断是否需要重新提取后再进行 qPCR。",
                Map.of(),
                "<h2>样本</h2><p>H12-1、H12-2、H12-3 剩余 RNA。</p><h2>计划</h2><p>复测浓度与 A260/A280，并通过 1% 琼脂糖凝胶观察 28S/18S 条带。</p>",
                List.of());
        spreadTimeline(project.id(), LocalDate.of(2026, 6, 18));
    }

    private void seedCellCultureProject(UUID owner, UUID member, UUID reviewer) {
        String name = "HEK293T 稳定细胞系构建";
        if (projectExists(name)) return;
        ProjectDtos.ProjectView project = createProject(owner, member, reviewer, name,
                "记录 HEK293T 慢病毒转染、筛选、扩增和冻存过程中的细胞状态。",
                "使用 HEK293T 细胞建立带荧光报告基因的稳定细胞系。重点记录细胞代次、铺板密度、转染后形态、筛选压力、支原体结果和冻存批次，确保后续功能实验使用状态一致的细胞。");

        RecordDtos.View passage = createRecord(member, project.id(), CELL_TEMPLATE,
                "HEK293T P18 常规传代与状态评估", "细胞培养", LocalDate.of(2026, 6, 27),
                "完成 P18 细胞常规传代，并评估转染前细胞形态、汇合度和污染风险。",
                cellValues("HEK293T", "实验室主细胞库 HEK293T-2026-01", "传代", 18, 82,
                        "细胞呈典型多角形贴壁，边界清晰，漂浮细胞少于 3%。", "支原体阴性"),
                "<h2>操作</h2><p>PBS 清洗一次，加入 0.05% Trypsin-EDTA 消化 2 分钟，完全培养基终止后按 1:5 分瓶至 T75 培养瓶。</p><h2>观察</h2><p>传代前汇合度约 82%，形态均一，无明显颗粒增多或污染迹象。</p>",
                List.of(markdown("HEK293T-P18-培养观察.md", "# HEK293T P18 观察\n\n- 汇合度：约 82%\n- 形态：多角形、贴壁均匀\n- 漂浮细胞：少于 3%\n- 支原体：阴性\n- 分瓶比例：1:5\n")));
        transition(member, reviewer, passage, Outcome.COMPLETED_R1, null, "细胞状态和支原体结果清晰，可用于后续转染。");

        RecordDtos.View transfection = createRecord(member, project.id(), CELL_TEMPLATE,
                "HEK293T 慢病毒转染后 48 h 观察", "细胞培养", LocalDate.of(2026, 7, 11),
                "观察慢病毒转染后 48 小时的细胞形态和荧光比例，判断是否进入嘌呤霉素筛选。",
                cellValues("HEK293T", "HEK293T-2026-01", "转染后观察", 20, 68,
                        "大部分细胞贴壁正常，约 8% 细胞变圆；荧光阳性比例约 62%。", "未见污染"),
                "<h2>处理条件</h2><p>MOI=5，转染增强剂 6 μg/mL，转染 12 h 后更换完全培养基。</p><h2>48 h 观察</h2><p>荧光阳性比例约 62%，细胞总体贴壁良好，少量细胞变圆。计划确认对照孔状态后开始 1.5 μg/mL 嘌呤霉素筛选。</p>",
                List.of());
        transition(member, reviewer, transfection, Outcome.IN_REVIEW, null, null);

        createRecord(owner, project.id(), CELL_TEMPLATE,
                "冻存前支原体筛查与活率检测", "细胞培养", LocalDate.of(2026, 7, 21),
                "在稳定株首批冻存前完成支原体筛查、活率检测和冻存密度确认。",
                cellValues("HEK293T-stable-puro", "稳定株筛选池 S1", "冻存", 24, 76,
                        "筛选后细胞恢复贴壁，大小较均一，未见明显空泡化。", "未检测支原体"),
                "<h2>待完成项目</h2><ul><li>支原体荧光法检测</li><li>台盼蓝计数与活率</li><li>按 2×10^6 cells/管配制冻存液</li></ul>",
                List.of());
        spreadTimeline(project.id(), LocalDate.of(2026, 6, 8));
    }

    private void seedArchivedCourseProject(UUID owner, UUID member, UUID reviewer) {
        String name = "2026 春季分子生物学课程归档";
        if (projectExists(name)) return;
        ProjectDtos.ProjectView project = createProject(owner, member, reviewer, name,
                "已完成的课程实验记录，用于演示只读归档、检索和报告导出。",
                "课程项目覆盖质粒转化后的菌落 PCR 鉴定和 16S rRNA 片段扩增。所有记录均完成审核，原始数据、实验结论和审核意见已固化为最终修订，项目归档后仅允许查看、搜索、下载和导出。");

        RecordDtos.View colony = createRecord(member, project.id(), PCR_TEMPLATE,
                "pUC19 转化菌落 PCR 鉴定", "菌落 PCR", LocalDate.of(2026, 5, 13),
                "从氨苄抗性平板挑取单克隆，通过菌落 PCR 筛选含目标插入片段的阳性克隆。",
                pcrValues("pUC19-C1-C8", "菌落裂解液", "pUC19-MCS 插入片段", 58.0, 620, "目标条带单一且清晰"),
                "<h2>结果</h2><p>C2、C4、C7 出现约 620 bp 目标条带；空载体对照约 180 bp；NTC 无扩增。</p><h2>结论</h2><p>选择 C2 和 C7 扩增培养并进行质粒小提与酶切复核。</p>",
                List.of(markdown("菌落PCR阳性克隆判读.md", "# 菌落 PCR 判读\n\n阳性克隆：C2、C4、C7。\n\n后续操作：优先培养 C2、C7，完成质粒小提和双酶切复核。\n")));
        transition(member, reviewer, colony, Outcome.COMPLETED_R1, null, "阳性克隆判定与对照设置完整，审核通过。");

        RecordDtos.View sixteenS = createRecord(member, project.id(), PCR_TEMPLATE,
                "环境分离菌 16S rRNA 片段扩增", "PCR", LocalDate.of(2026, 5, 20),
                "扩增环境分离菌的 16S rRNA 基因片段，为后续测序鉴定提供模板。",
                pcrValues("ENV-16S-03", "基因组 DNA", "16S rRNA V1–V9", 55.0, 1500, "目标条带单一且清晰"),
                "<h2>结果</h2><p>样本 ENV-03 获得约 1.5 kb 单一条带，阴性对照无扩增，PCR 产物浓度满足测序送样要求。</p><h2>结论</h2><p>产物已完成纯化并送 Sanger 双向测序。</p>",
                List.of());
        transition(member, reviewer, sixteenS, Outcome.COMPLETED_R1, null, "条带大小和阴性对照符合预期，可归档。");
        projectService.archive(owner, project.id());
        spreadTimeline(project.id(), LocalDate.of(2026, 4, 20));
    }

    private ProjectDtos.ProjectView createProject(UUID owner, UUID member, UUID reviewer, String name,
                                                   String description, String detail) {
        ProjectDtos.ProjectView project = projectService.create(owner,
                new ProjectDtos.CreateProjectRequest(name, description, detail));
        inviteAndAccept(owner, project.id(), member, email(member));
        inviteAndAccept(owner, project.id(), reviewer, email(reviewer));
        projectService.changeRole(owner, project.id(), reviewer,
                new ProjectDtos.RoleRequest("REVIEWER", Map.of()));
        return project;
    }

    private void inviteAndAccept(UUID owner, UUID project, UUID invitee, String email) {
        ProjectDtos.InvitationView invitation = projectService.invite(owner, project, new ProjectDtos.InviteRequest(email));
        projectService.accept(invitee, invitation.id());
    }

    private RecordDtos.View createRecord(UUID creator, UUID project, UUID template, String title, String type,
                                         LocalDate date, String purpose, Map<String, Object> fieldValues,
                                         String contentHtml, List<AttachmentSeed> attachments) {
        RecordDtos.View created = recordService.create(creator,
                new RecordDtos.CreateRequest(project, template, title, type, date, purpose));
        Map<String, Object> values = new LinkedHashMap<>(fieldValues);
        RecordDtos.View current = recordService.update(creator, created.id(),
                new RecordDtos.UpdateRequest(created.version(), title, type, date, purpose, values,
                        Map.of("type", "doc"), contentHtml));
        Map<String, List<String>> linkedFiles = new LinkedHashMap<>();
        for (AttachmentSeed attachment : attachments) {
            UUID attachmentId = attachmentService.upload(creator, current.id(), attachment.file()).id();
            if (attachment.fieldKey() != null) {
                linkedFiles.computeIfAbsent(attachment.fieldKey(), ignored -> new ArrayList<>()).add(attachmentId.toString());
            }
        }
        if (!linkedFiles.isEmpty()) {
            values.putAll(linkedFiles);
            current = recordService.update(creator, current.id(),
                    new RecordDtos.UpdateRequest(current.version(), title, type, date, purpose, values,
                            Map.of("type", "doc"), contentHtml));
        }
        alignRecordTimes(current.id(), date);
        return recordService.get(creator, current.id());
    }

    private void transition(UUID creator, UUID reviewer, RecordDtos.View record, Outcome outcome,
                            String changesComment, String approvalComment) {
        ReviewDtos.RevisionView r1 = reviewService.submit(creator, record.id(),
                new ReviewDtos.SubmitRequest(reviewer, "演示数据：提交 R1 审核", record.version()),
                "seed:" + record.id() + ":r1");
        if (outcome == Outcome.IN_REVIEW) return;
        if (outcome == Outcome.CHANGES_REQUESTED) {
            reviewService.requestChanges(reviewer, record.id(), r1.review().id(), changesComment);
            return;
        }
        if (outcome == Outcome.COMPLETED_R1) {
            reviewService.approve(reviewer, record.id(), r1.review().id(), approvalComment);
            return;
        }
        reviewService.requestChanges(reviewer, record.id(), r1.review().id(), changesComment);
        RecordDtos.View changed = recordService.get(creator, record.id());
        Map<String,Object> revisedValues = new LinkedHashMap<>(changed.fieldValues());
        if (outcome == Outcome.CHANGES_REQUESTED_R2) {
            revisedValues.put("relative_expression", 3.42);
            revisedValues.put("melt_curve_result", "存在多峰或杂峰");
            attachmentService.upload(creator, record.id(), markdown("VEGFA-R2-扩增曲线复核.md", "# VEGFA R2 扩增曲线复核\n\n- H12-2 A9 复孔偏离 0.71 Ct\n- 熔解曲线肩峰仍可见\n- 建议补充引物特异性与异常孔剔除标准\n").file());
        }
        String revisedPurpose = changed.purpose() + " 已根据 R1 审核意见补充对照解释并完成复核。";
        String revisedHtml = changed.contentHtml() + "<h2>R1 退回后补充</h2><p>已核对原始数据、阴性对照和判读标准，并补充选择依据。</p>";
        RecordDtos.View revised = recordService.update(creator, record.id(),
                new RecordDtos.UpdateRequest(changed.version(), outcome == Outcome.CHANGES_REQUESTED_R2 ? changed.title() + "（R2 复核）" : changed.title(), changed.experimentType(),
                        changed.experimentDate(), revisedPurpose, revisedValues, changed.contentJson(), revisedHtml));
        ReviewDtos.RevisionView r2 = reviewService.submit(creator, record.id(),
                new ReviewDtos.SubmitRequest(reviewer, "已按 R1 意见补充，提交 R2", revised.version()),
                "seed:" + record.id() + ":r2");
        if (outcome == Outcome.CHANGES_REQUESTED_R2) reviewService.requestChanges(reviewer, record.id(), r2.review().id(), "R2 已补充曲线复核，但仍需给出引物特异性验证结果后再批准。");
        else reviewService.approve(reviewer, record.id(), r2.review().id(), approvalComment);
    }

    private Map<String, Object> pcrValues(String sample, String source, String target, double annealing,
                                          int size, String result) {
        return values(
                "sample_code", sample,
                "template_source", source,
                "target_locus", target,
                "primer_pair", "Forward primer / Reverse primer 各 10 μM；工作浓度各 0.4 μM",
                "polymerase_mix", "高保真 PCR Mix",
                "reaction_volume", 25,
                "reaction_system", "2× High-Fidelity Mix 12.5 μL；F/R primer 各 1.0 μL；模板 1.0 μL；无核酸酶水补至 25 μL",
                "annealing_temperature", annealing,
                "extension_time", 45,
                "cycle_count", 35,
                "expected_product_size", size,
                "control_design", "阳性对照：已验证模板；阴性对照：无模板对照（NTC）",
                "amplification_result", result
        );
    }

    private Map<String, Object> qpcrValues(String groups, String target, String reference, double expression,
                                           String meltCurve, String conclusion) {
        return values(
                "sample_group", groups,
                "extraction_summary", "柱式 RNA 提取试剂盒，柱上 DNase I 处理；每份 1 μg RNA 逆转录，cDNA 稀释 5 倍使用",
                "rna_concentration", 286.4,
                "a260_280", 1.96,
                "target_gene", target,
                "reference_gene", reference,
                "primer_info", target + " 与 " + reference + " 引物终浓度均为 0.2 μM，扩增片段 90–160 bp",
                "qpcr_mix", "SYBR Green qPCR Mix",
                "reaction_volume", 10,
                "replicate_design", "每组 3 个生物学重复，每个样本 3 个技术重复；设置 NTC 和 -RT 对照",
                "cycling_program", "95℃ 30 s；95℃ 5 s、60℃ 30 s，40 cycles；65–95℃ 熔解曲线",
                "ct_summary", "目标基因平均 Ct 24–30；内参平均 Ct 19–20；除特别说明外技术重复 SD < 0.25 Ct",
                "melt_curve_result", meltCurve,
                "relative_expression", expression,
                "result_conclusion", conclusion
        );
    }

    private Map<String, Object> cellValues(String cellLine, String source, String operation, int passage,
                                            int confluence, String morphology, String contamination) {
        return values(
                "cell_line", cellLine,
                "source_lot", source,
                "operation_type", operation,
                "passage_number", passage,
                "culture_vessel", "T75 培养瓶",
                "medium_composition", "高糖 DMEM + 10% FBS + 1% 青霉素/链霉素",
                "serum_lot", "FBS-2026-03",
                "seeding_density", 25000,
                "confluence", confluence,
                "morphology", morphology,
                "incubator_conditions", "37℃、5% CO₂、饱和湿度",
                "dissociation_and_split", "0.05% Trypsin-EDTA 消化 2 min，完全培养基终止，按 1:5 分瓶",
                "viability", 94.2,
                "contamination_check", contamination,
                "next_observation_date", LocalDate.of(2026, 7, 24).toString()
        );
    }

    private Map<String, Object> values(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put(pairs[i].toString(), pairs[i + 1]);
        return result;
    }

    private AttachmentSeed markdown(String filename, String content) {
        return new AttachmentSeed(new SeedMultipartFile(filename, "text/markdown", content.getBytes(StandardCharsets.UTF_8)), null);
    }

    private AttachmentSeed csv(String filename, String content, String fieldKey) {
        return new AttachmentSeed(new SeedMultipartFile(filename, "text/csv", content.getBytes(StandardCharsets.UTF_8)), fieldKey);
    }

    private void alignRecordTimes(UUID recordId, LocalDate date) {
        Instant created = date.atTime(9, 0).toInstant(ZoneOffset.UTC);
        jdbc.update("UPDATE experiment_records SET created_at=?,updated_at=? WHERE id=?",
                Timestamp.from(created), Timestamp.from(created.plus(6, ChronoUnit.HOURS)), recordId.toString());
        jdbc.update("UPDATE attachments SET created_at=? WHERE record_id=?",
                Timestamp.from(created.plus(2, ChronoUnit.HOURS)), recordId.toString());
    }

    private void spreadTimeline(UUID projectId, LocalDate startDate) {
        List<String> events = jdbc.queryForList(
                "SELECT id FROM audit_events WHERE project_id=? ORDER BY created_at,id", String.class, projectId.toString());
        Instant start = startDate.atTime(9, 0).toInstant(ZoneOffset.UTC);
        Instant last = start;
        for (int i = 0; i < events.size(); i++) {
            last = start.plus(i * 30L, ChronoUnit.HOURS);
            jdbc.update("UPDATE audit_events SET created_at=? WHERE id=?", Timestamp.from(last), events.get(i));
        }
        jdbc.update("UPDATE projects SET created_at=?,updated_at=? WHERE id=?",
                Timestamp.from(start), Timestamp.from(last), projectId.toString());
    }

    private boolean projectExists(String name) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT COUNT(*)>0 FROM projects WHERE name=?", Boolean.class, name));
    }

    private void ensureAccount(Account account) {
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email_normalized=?", Integer.class, account.email());
        if (existing != null && existing == 0) {
            authService.register(new AuthDtos.RegisterRequest(account.displayName(), account.email(), password));
        }
    }

    private UUID userId(String email) {
        return UUID.fromString(jdbc.queryForObject(
                "SELECT id FROM users WHERE email_normalized=?", String.class, email));
    }

    private String email(UUID userId) {
        return jdbc.queryForObject("SELECT email_normalized FROM users WHERE id=?", String.class, userId.toString());
    }

    private enum Outcome { IN_REVIEW, CHANGES_REQUESTED, CHANGES_REQUESTED_R2, COMPLETED_R1, COMPLETED_R2 }
    private record Account(String displayName, String email) {}
    private record AttachmentSeed(SeedMultipartFile file, String fieldKey) {}

    private static final class SeedMultipartFile implements MultipartFile {
        private final String filename;
        private final String contentType;
        private final byte[] content;

        private SeedMultipartFile(String filename, String contentType, byte[] content) {
            this.filename = filename;
            this.contentType = contentType;
            this.content = content;
        }

        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return filename; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return content.length == 0; }
        @Override public long getSize() { return content.length; }
        @Override public byte[] getBytes() { return content.clone(); }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(content); }
        @Override public void transferTo(File destination) throws IOException { Files.write(destination.toPath(), content); }
    }
}
