package com.bionote.agent.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pluggable narrative/analysis frameworks for project chat. These do NOT change experiment-record
 * storage or the curve-fit engine; they only constrain how the assistant structures explanations
 * and which evidence it may use. Domain-specific kits (e.g. fermentation kinetics) register here as
 * one template among others.
 */
public final class AnalysisTemplateCatalog {
    public record SuggestedEvidenceFit(String purpose, String xHint, String yHint, String note) {}

    public record Template(
            String id,
            String label,
            String description,
            List<String> aliases,
            List<String> outputSections,
            List<String> uncertaintyChecklist,
            List<SuggestedEvidenceFit> suggestedFits,
            String systemAddendum) {}

    private static final List<Template> TEMPLATES =
            List.of(
                    processKineticsProxy(),
                    doseResponseNarrative(),
                    experimentSummary(),
                    projectStatusReport(),
                    csvDataAnalysis());

    private static final Map<String, String> ALIAS_TO_ID = new LinkedHashMap<>();

    static {
        for (Template template : TEMPLATES) {
            ALIAS_TO_ID.put(template.id().toLowerCase(Locale.ROOT), template.id());
            ALIAS_TO_ID.put(template.label().toLowerCase(Locale.ROOT), template.id());
            for (String alias : template.aliases()) {
                ALIAS_TO_ID.put(alias.toLowerCase(Locale.ROOT), template.id());
            }
        }
    }

    private AnalysisTemplateCatalog() {}

    public static List<Template> all() {
        return TEMPLATES;
    }

    public static Template byId(String id) {
        if (id == null || id.isBlank()) return null;
        String key = id.trim().toLowerCase(Locale.ROOT);
        for (Template template : TEMPLATES) {
            if (template.id().equalsIgnoreCase(key)) return template;
        }
        return null;
    }

    public static Template resolveFromMessage(String message) {
        if (message == null || message.isBlank()) return null;
        String text = message.toLowerCase(Locale.ROOT);
        // Longer aliases first
        List<Map.Entry<String, String>> entries = new ArrayList<>(ALIAS_TO_ID.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
        for (Map.Entry<String, String> entry : entries) {
            if (text.contains(entry.getKey()) || message.contains(entry.getKey())) {
                return byId(entry.getValue());
            }
        }
        if (looksLikeAnalysisRequest(message)
                && (text.contains("动力学")
                        || text.contains("发酵")
                        || text.contains("耗碱")
                        || text.contains("残糖")
                        || text.contains("溶氧"))) {
            return byId("process_kinetics_proxy");
        }
        if (looksLikeAnalysisRequest(message) && (text.contains("剂量") || text.contains("dose"))) {
            return byId("dose_response_narrative");
        }
        if (looksLikeAnalysisRequest(message)
                && (text.contains("实验总结")
                        || text.contains("western")
                        || text.contains("blot")
                        || text.contains("pcr")
                        || text.contains("总结这个实验")
                        || text.contains("总结")
                        || text.contains("分析这个实验")
                        || text.contains("实验分析"))) {
            return byId("experiment_summary");
        }
        if (looksLikeAnalysisRequest(message)
                && (text.contains("项目进展")
                        || text.contains("项目状态")
                        || text.contains("进度报告")
                        || text.contains("项目总结"))) {
            return byId("project_status_report");
        }
        if (looksLikeAnalysisRequest(message)
                && (text.contains("csv")
                        || text.contains("数据拟合")
                        || text.contains("标准曲线")
                        || text.contains("标曲"))) {
            return byId("csv_data_analysis");
        }
        return null;
    }

    public static boolean looksLikeAnalysisRequest(String message) {
        if (message == null || message.isBlank()) return false;
        String text = message.toLowerCase(Locale.ROOT);
        return message.contains("分析报告")
                || message.contains("机制分析")
                || message.contains("动力学分析")
                || message.contains("按模型分析")
                || message.contains("过程分析")
                || message.contains("条件比较")
                || message.contains("总结")
                || message.contains("分析")
                || message.contains("报告")
                || text.contains("ysm01")
                || message.contains("混合动力学")
                || message.contains("分析框架");
    }

    public static String catalogDescription() {
        StringBuilder sb = new StringBuilder();
        for (Template template : TEMPLATES) {
            sb.append("- id=")
                    .append(template.id())
                    .append(" label=")
                    .append(template.label())
                    .append(" desc=")
                    .append(template.description())
                    .append('\n');
        }
        return sb.toString();
    }

    /**
     * General process-kinetics narrative lens. Applies to fermentation and any batch process with
     * biomass proxy / substrate / DO / acid proxy columns. YSM01 / 嗜盐菌 is an alias, not a separate
     * code path.
     */
    private static Template processKineticsProxy() {
        String addendum =
                """
                ANALYSIS_MODE=process_kinetics_proxy
                You must structure the answer using the output sections of this template.
                Treat the listed kinetic equations as an INTERPRETIVE FRAMEWORK only.
                Do NOT numerically solve ODEs and do NOT invent kinetic parameters (mu_max, Ks, KO, qS, qA, qP, kOD, psi, etc.).
                Evidence hierarchy:
                1) FIT_RESULT / FIT_RESULTS numbers if present
                2) FIT_DATA_CATALOG column names and record metadata
                3) Explicit user statements in chat history
                If a quantity was not fitted or not measured, write "无法从当前数据估计" under 分析边界.
                Mark mechanistic sentences as 假说 when they go beyond fitted/observed evidence.
                When comparing multiple records, only compare fields that appear in the catalog.
                """;
        return new Template(
                "process_kinetics_proxy",
                "过程动力学代理分析",
                "用混合动力学话术组织解释；以拟合与观测为证据，不求解 ODE、不编造动力学参数。适用于发酵等批次过程，YSM01 等场景共用此模板。",
                List.of(
                        "YSM01", "ysm01", "嗜盐菌", "发酵动力学", "混合动力学", "过程动力学", "动力学模型", "耗碱代理",
                        "PHA框架"),
                List.of(
                        "实验条件与数据范围",
                        "生物量/OD（对应 dX/dt、μ、OD=kOD·X·ψ）",
                        "糖代谢（对应 dS/dt、Feed/残糖）",
                        "酸生成/耗碱代理（对应 dA/dt≈耗碱）",
                        "溶氧与温度约束（对应 μ 中的 DO、fT）",
                        "产物/PHA（无数据则明确无法估计）",
                        "条件比较与异常/事件",
                        "机制假说（标注假说）",
                        "潜在优化窗口与后续实验建议",
                        "分析边界与不确定性"),
                List.of(
                        "糖液浓度",
                        "真实补糖质量/Feed",
                        "稀释率 D",
                        "真实生物量 X",
                        "PHA 含量",
                        "分子量 Mn/Mw/DPI",
                        "基因表达强度",
                        "细胞形态因子 ψ"),
                List.of(
                        new SuggestedEvidenceFit("OD 增长", "时间", "OD", "时间建议转为相对分钟"),
                        new SuggestedEvidenceFit("残糖消耗", "时间", "残糖", "结合备注中的补糖事件"),
                        new SuggestedEvidenceFit("耗碱/酸代理", "时间或OD", "耗碱量", "列名可能为耗碱量（显示值）"),
                        new SuggestedEvidenceFit("溶氧轨迹", "时间", "溶氧", "可对照搅拌/通风量")),
                addendum);
    }

    /**
     * General experiment summary template — covers Western Blot, PCR, ELISA, etc. Provides
     * structured sections for experiment purpose, materials, steps, results, anomaly analysis, and
     * improvement suggestions.
     */
    private static Template experimentSummary() {
        String addendum =
                """
                ANALYSIS_MODE=experiment_summary
                Structure the answer using the output sections below. Use ONLY the RECORD_CONTEXT provided.
                Never invent experimental results, band intensities, molecular weights, or numerical values
                that are not present in the context. For Western Blot, describe bands qualitatively only if
                the context mentions them. Mark any inference as [推断] when it goes beyond explicit data.
                """;
        return new Template(
                "experiment_summary",
                "实验记录智能总结",
                "按实验目的、材料、步骤、结果、异常、建议等维度结构化总结实验记录；不编造未在记录中出现的数据。",
                List.of(
                        "western blot",
                        "western",
                        "WB",
                        "blot",
                        "pcr",
                        "PCR",
                        "elisa",
                        "ELISA",
                        "实验总结",
                        "实验分析",
                        "帮我总结这个实验",
                        "分析这个实验",
                        "总结实验",
                        "帮我总结这个",
                        "总结这个Western"),
                List.of("实验目的", "实验材料与试剂", "实验步骤概要", "实验结果与观察", "异常分析与注意", "改进建议与下一步"),
                List.of("抗体信息", "曝光时间", "显影条件", "定量数值", "重复次数", "阳性/阴性对照"),
                List.of(),
                addendum);
    }

    /** Project status report template — provides structured project progress overview. */
    private static Template projectStatusReport() {
        String addendum =
                """
                ANALYSIS_MODE=project_status_report
                Structure the answer using the output sections. Use only PROJECT_CONTEXT data.
                COMPLETED means workflow completion, not experimental success.
                Clearly distinguish factual observations from suggestions.
                """;
        return new Template(
                "project_status_report",
                "项目进展报告",
                "按完成实验、未完成任务、风险、下一步建议的结构化维度总结项目进展。",
                List.of("项目进展", "项目状态", "进度报告", "项目总结", "进展如何", "告诉我进展", "项目进度", "status report"),
                List.of("已完成实验", "进行中与待审核", "风险与阻塞", "下一步建议"),
                List.of("成员参与度", "实验成功率", "审核瓶颈", "资源约束"),
                List.of(),
                addendum);
    }

    /** CSV/data analysis template — guides the model to propose fitting and interpret results. */
    private static Template csvDataAnalysis() {
        String addendum =
                """
                ANALYSIS_MODE=csv_data_analysis
                When FIT_DATA_CATALOG is provided, examine the column names and types.
                Suggest which columns could serve as x (independent) and y (dependent) variables.
                When the user uploads a CSV, propose a fit plan before executing.
                If FIT_RESULTS are present, explain the results in plain language.
                Never invent fit parameters unless they appear in FIT_RESULTS.
                """;
        return new Template(
                "csv_data_analysis",
                "实验数据分析",
                "识别上传的CSV/Excel数据，建议自变量与因变量映射，解释拟合结果。",
                List.of(
                        "csv分析",
                        "数据分析",
                        "数据拟合",
                        "标准曲线",
                        "标曲",
                        "拟合分析",
                        "帮我分析数据",
                        "analyze data",
                        "curve fitting"),
                List.of("数据概览", "变量识别与映射建议", "拟合方案推荐", "结果解读", "注意事项"),
                List.of("数据完整性", "异常值", "拟合优度", "适用条件"),
                List.of(
                        new SuggestedEvidenceFit("线性拟合", "待定", "待定", "根据CSV列名自动建议"),
                        new SuggestedEvidenceFit(
                                "标准曲线", "log10_copies或concentration", "ct", "适用于qPCR数据")),
                addendum);
    }

    /** Generic dose–response narrative for pharmacology/toxicology style tables. */
    private static Template doseResponseNarrative() {
        String addendum =
                """
                ANALYSIS_MODE=dose_response_narrative
                Structure the answer with the template sections.
                Prefer confirmed FIT_RESULTS. Never invent IC50/EC50 unless present in fit results or record fields.
                State uncertainty when replicate or control arms are missing.
                """;
        return new Template(
                "dose_response_narrative",
                "剂量反应叙述分析",
                "按剂量–反应框架组织解释；参数以拟合结果为准。",
                List.of("剂量反应", "dose-response", "dose response", "IC50分析", "EC50分析"),
                List.of("实验设计与剂量轴", "响应指标与拟合证据", "效应解读（假说级）", "风险与边界条件", "后续实验建议", "分析边界与不确定性"),
                List.of("生物重复", "载剂对照", "时间点完整性"),
                List.of(
                        new SuggestedEvidenceFit(
                                "剂量反应曲线",
                                "dose或concentration",
                                "viability或响应列",
                                "可用线性/对数/指数衰减等预置方程")),
                addendum);
    }
}
