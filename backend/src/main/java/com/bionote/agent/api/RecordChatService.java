package com.bionote.agent.api;

import com.bionote.agent.analysis.AnalysisTemplateCatalog;
import com.bionote.agent.config.AgentCredentialService;
import com.bionote.agent.config.AgentCredentials;
import com.bionote.agent.config.AgentProperties;
import com.bionote.agent.fit.ChartPlotService;
import com.bionote.agent.fit.CurveFitService;
import com.bionote.agent.fit.FitMethodCatalog;
import com.bionote.agent.fit.FitModels;
import com.bionote.agent.fit.FitProposalResolver;
import com.bionote.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class RecordChatService implements AgentChatUseCase {
    private static final String RECORD_SYSTEM = """
            You are BioNote's read-only assistant for one experiment record.
            Answer only from the provided record context and chat history.
            If facts are missing, say you do not know. Never invent experimental outcomes, reviews, or revision numbers.
            Never claim you modified the record, reviews, attachments, or membership.
            Never output email addresses, secrets, storage paths, or hidden reasoning.
            Reply in the user's language. Keep answers concise and practical.
            """;

    private static final String PROJECT_SYSTEM = """
            You are BioNote's read-only assistant for one collaboration project.
            Answer only from the provided project context and chat history.
            If facts are missing, say you do not know. Never invent experimental outcomes, reviews, or member emails.
            COMPLETED means workflow completion, not experimental success.
            Never claim you modified projects, records, reviews, attachments, or membership.
            When CHAT_FILE_REFERENCES is provided, treat those temporary uploads as user-supplied evidence for this turn only; they are not experiment-record attachments.
            Never invent fit parameters; when FIT_RESULT / FIT_RESULTS are provided, explain those numbers only.
            When FIT_PROPOSAL is provided, summarize the proposed data range and method and ask the user to confirm before fitting. Always keep the structured proposal; the UI shows a confirm button.
            When FIT_DATA_CATALOG lists file="..." columns=[...], those ARE the Excel/CSV headers — answer column questions from them; never say you cannot see attachment columns.
            NEVER invent regression coefficients, R², or RMSE when FIT_RESULT is absent. If no FIT_RESULT is provided, do not claim fitting was executed.
            When CHART is provided, describe that verified chart briefly; never invent series values beyond CHART.
            When ANALYSIS_TEMPLATE is provided, follow its systemAddendum and outputSections; kinetic equations are interpretive only — never invent ODE parameters.
            ANALYSIS_TEMPLATE_CATALOG lists reusable analysis frameworks available in this product.
            Never output email addresses, secrets, storage paths, or hidden reasoning.
            Reply in the user's language. Keep answers concise and practical.
            """;

    private static final Logger log = LoggerFactory.getLogger(RecordChatService.class);
    private final AgentProperties properties;
    private final AgentCredentialService credentials;
    private final AgentChatContextStore contextStore;
    private final ObjectMapper json;
    private final CurveFitService curveFits;
    private final FitProposalResolver proposals;
    private final AgentChatReferenceService chatReferences;
    private final ChartPlotService charts;

    public RecordChatService(AgentProperties properties, AgentCredentialService credentials, AgentChatContextStore contextStore,
                             ObjectMapper json, CurveFitService curveFits, FitProposalResolver proposals,
                             AgentChatReferenceService chatReferences, ChartPlotService charts) {
        this.properties = properties;
        this.credentials = credentials;
        this.contextStore = contextStore;
        this.json = json;
        this.curveFits = curveFits;
        this.proposals = proposals;
        this.chatReferences = chatReferences;
        this.charts = charts;
    }

    public AgentDtos.ChatReply chat(UUID actor, UUID recordId, AgentDtos.ChatRequest request) {
        requireEnabled();
        Map<String, Object> record = requireVisibleRecord(actor, recordId);
        ParsedChat parsed = parseRequest(request);
        String context = buildRecordContext(record);

        // Resolve analysis template from user message
        AnalysisTemplateCatalog.Template analysisTemplate =
                AnalysisTemplateCatalog.resolveFromMessage(parsed.message());
        String systemPrompt = RECORD_SYSTEM;
        String contextLabel = "RECORD_CONTEXT";

        if (analysisTemplate != null) {
            systemPrompt = systemPrompt + "\n" + analysisTemplate.systemAddendum();
            AgentDtos.AnalysisTemplateView templateView = toAnalysisTemplateView(analysisTemplate);
            context = context + "\n\nANALYSIS_TEMPLATE:\n" + write(templateView)
                    + "\nOUTPUT_SECTIONS: " + String.join("；", analysisTemplate.outputSections());
            // Enrich context with revision and review data for deeper analysis
            context = enrichRecordContextForAnalysis(context, recordId);
            AgentCredentials creds = credentials.resolve(actor);
            AgentDtos.ChatReply fake = fakeAnalysisRecordReply(record, analysisTemplate, parsed.message(), creds);
            return complete(creds, systemPrompt, contextLabel, context, parsed, fake,
                    null, null, null, templateView, null);
        }

        AgentCredentials creds = credentials.resolve(actor);
        return complete(creds, systemPrompt, contextLabel, context, parsed,
                fakeRecordReply(record, parsed.message(), creds), null, null, null, null, null);
    }

    /** Enrich record context with recent revision and review summaries for analysis. */
    private String enrichRecordContextForAnalysis(String context, UUID recordId) {
        StringBuilder extra = new StringBuilder();
        try {
            List<Map<String,Object>> revisions=contextStore.recentRevisions(recordId);
            if (!revisions.isEmpty()) {
                extra.append("\nRECENT_REVISIONS:\n");
                for (Map<String, Object> rev : revisions) {
                    extra.append("- R").append(rev.get("revision_no"))
                          .append(" by ").append(rev.get("submitter"))
                          .append(": ").append(limit(String.valueOf(rev.get("submit_note")), 200))
                          .append("\n");
                }
            }
            List<Map<String,Object>> reviews=contextStore.recentReviews(recordId);
            if (!reviews.isEmpty()) {
                extra.append("\nRECENT_REVIEWS:\n");
                for (Map<String, Object> rv : reviews) {
                    extra.append("- ").append(rv.get("status"))
                          .append(" by ").append(rv.get("reviewer"))
                          .append(": ").append(limit(String.valueOf(rv.get("decision_comment")), 300))
                          .append("\n");
                }
            }
            List<Map<String,Object>> attachments=contextStore.activeAttachments(recordId);
            if (!attachments.isEmpty()) {
                extra.append("\nATTACHMENTS:\n");
                for (Map<String, Object> att : attachments) {
                    extra.append("- ").append(att.get("original_filename"))
                          .append(" (").append(att.get("content_type"))
                          .append(", ").append(formatBytes(att.get("size_bytes"))).append(")\n");
                }
            }
        } catch (Exception ignored) {
            // enrichment is best-effort
        }
        return context + extra.toString();
    }

    private String formatBytes(Object size) {
        if (size == null) return "?";
        long bytes = ((Number) size).longValue();
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private AgentDtos.ChatReply fakeAnalysisRecordReply(Map<String, Object> record,
                                                         AnalysisTemplateCatalog.Template template,
                                                         String message, AgentCredentials creds) {
        String code = String.valueOf(record.get("code"));
        String title = String.valueOf(record.get("title"));
        String sections = String.join("；", template.outputSections());
        return new AgentDtos.ChatReply(
                "【本地 fake 回复】已读取记录 " + code + "《" + title + "》，"
                        + "启用分析模板「" + template.label() + "」（id=" + template.id() + "）。"
                        + "当前状态为 " + record.get("status") + "。"
                        + "输出章节：" + sections + "。"
                        + "真实模型未启用时仅返回确定性演示答复。",
                "fake",
                displayModel(creds),
                null, null, null,
                toAnalysisTemplateView(template), null);
    }

    public AgentDtos.ChatReply chatAboutProject(UUID actor, UUID projectId, AgentDtos.ChatRequest request) {
        requireEnabled();
        Map<String, Object> project = requireVisibleProject(actor, projectId);
        ParsedChat parsed = parseRequest(request);
        String context = buildProjectContext(project);
        List<FitModels.CatalogRecord> catalog = curveFits.buildFitCatalog(projectId);
        String catalogText = curveFits.formatFitCatalog(catalog);
        context = context + "\n\nFIT_DATA_CATALOG:\n" + catalogText
                + "\nEQUATION_CATALOG:\n" + FitMethodCatalog.catalogDescription()
                + "\nANALYSIS_TEMPLATE_CATALOG:\n" + AnalysisTemplateCatalog.catalogDescription();
        String referenceContext = chatReferences.formatForContext(actor, projectId, request.referenceIds());
        if (referenceContext != null && !referenceContext.isBlank()) {
            context = context + "\n\n" + referenceContext;
        }

        if (request.fitConfirm() != null) {
            return executeConfirmedFit(actor, projectId, project, parsed, context, request.fitConfirm());
        }

        AnalysisTemplateCatalog.Template analysisTemplate = AnalysisTemplateCatalog.resolveFromMessage(parsed.message());
        boolean preferAnalysis = analysisTemplate != null
                || AnalysisTemplateCatalog.looksLikeAnalysisRequest(parsed.message());
        if (preferAnalysis && analysisTemplate == null) {
            analysisTemplate = AnalysisTemplateCatalog.byId("process_kinetics_proxy");
        }

        AgentCredentials creds = credentials.resolve(actor);

        // Curve-fit proposal path (skip when user primarily asks for template analysis report).
        if (!preferAnalysis && (curveFits.looksLikeFitRequest(parsed.message()) || isConfirmPhrase(parsed.message()))) {
            if (isConfirmPhrase(parsed.message()) && request.fitConfirm() == null) {
                return new AgentDtos.ChatReply(
                        "请点击方案卡片上的「确认拟合」按钮（不要只打「确认」）。若按钮未出现，请重新描述拟合需求以生成结构化方案。",
                        displayProvider(creds), displayModel(creds), null, null, null, null, null);
            }
            FitModels.FitIntent seed = curveFits.parseIntent(parsed.message());
            String historyHint = recentUserHints(parsed.history());
            String resolveMessage = historyHint.isEmpty() ? parsed.message() : historyHint + "\n" + parsed.message();
            FitModels.FitProposal proposal = proposals.resolve(resolveMessage, catalogText, seed, creds);
            if (proposal.missingPrompt() != null) {
                return new AgentDtos.ChatReply(proposal.missingPrompt(), displayProvider(creds), displayModel(creds), null, null, null, null, null);
            }
            AgentDtos.FitProposalView proposalView = toProposalView(proposal);
            String proposalContext = "\n\nFIT_PROPOSAL:\n" + write(proposalView);
            AgentDtos.ChatReply fake = fakeProposalReply(project, proposalView, creds);
            return complete(creds, PROJECT_SYSTEM, "PROJECT_CONTEXT", context + proposalContext, parsed, fake,
                    null, null, proposalView, null, null);
        }

        if (!preferAnalysis && charts.shouldHandleChart(parsed.message(), parsed.history())) {
            String historyHint = recentUserHints(parsed.history());
            String resolveMessage = historyHint.isEmpty() ? parsed.message() : historyHint + "\n" + parsed.message();
            try {
                AgentDtos.ChartView chart = charts.plot(actor, projectId, resolveMessage, catalogText, creds);
                int n = chart.series() == null || chart.series().isEmpty() || chart.series().get(0).points() == null
                        ? 0 : chart.series().get(0).points().size();
                String reply = "已根据表格数据生成「" + chart.title() + "」（" + n + " 个点）。"
                        + "下图数值来自 Excel/CSV 抽取，未由模型编造。";
                // Skip the free-form LLM turn so it cannot invent markdown placeholder images.
                return new AgentDtos.ChatReply(reply, displayProvider(creds), displayModel(creds),
                        null, null, null, null, chart);
            } catch (ApiException e) {
                String message = e.getMessage() == null ? "无法生成数据图" : e.getMessage();
                return new AgentDtos.ChatReply(message, displayProvider(creds), displayModel(creds),
                        null, null, null, null, null);
            }
        }

        if (preferAnalysis && analysisTemplate != null) {
            AgentDtos.AnalysisTemplateView templateView = toAnalysisTemplateView(analysisTemplate);
            String analysisContext = "\n\nANALYSIS_TEMPLATE:\n" + write(templateView)
                    + "\nANALYSIS_TEMPLATE_RULES:\n" + analysisTemplate.systemAddendum();
            AgentDtos.ChatReply fake = fakeAnalysisReply(project, templateView, creds);
            return complete(creds, PROJECT_SYSTEM + "\n" + analysisTemplate.systemAddendum(),
                    "PROJECT_CONTEXT", context + analysisContext, parsed, fake,
                    null, null, null, templateView, null);
        }

        AgentDtos.ChatReply fake = fakeProjectReply(project, parsed.message(), null, creds);
        return complete(creds, PROJECT_SYSTEM, "PROJECT_CONTEXT", context, parsed, fake, null, null, null, null, null);
    }

    private AgentDtos.ChatReply executeConfirmedFit(UUID actor, UUID projectId, Map<String, Object> project,
                                                   ParsedChat parsed, String context, AgentDtos.FitProposalView confirm) {
        AgentCredentials creds = credentials.resolve(actor);
        FitModels.FitProposal proposal = fromProposalView(confirm);
        try {
            List<FitModels.FitResult> results = curveFits.fitProposalAll(actor, projectId, proposal);
            List<AgentDtos.FitView> fitViews = results.stream().map(this::toFitView).toList();
            AgentDtos.FitView primary = fitViews.isEmpty() ? null : fitViews.get(0);
            AgentDtos.ChartView chart = charts.fromFit(primary);
            String fitContext = "\n\nFIT_RESULTS:\n" + write(fitViews)
                    + (chart == null ? "" : "\n\nCHART:\n" + write(chart));
            AgentDtos.ChatReply fake = fakeProjectReply(project, parsed.message(), primary, fitViews, creds);
            return complete(creds, PROJECT_SYSTEM, "PROJECT_CONTEXT", context + fitContext, parsed, fake, primary, fitViews, null, null, chart);
        } catch (ApiException e) {
            String message = e.getMessage() == null ? "拟合失败" : e.getMessage();
            if ("invalid number".equalsIgnoreCase(message) || message.toLowerCase(Locale.ROOT).contains("invalid number")) {
                message = "数据或方程无法解析为数值。若时间列为时刻，请说明“转换为分钟”；多元回归请用列名列表而非 y=a+b1*x1+... 省略式。";
            }
            return new AgentDtos.ChatReply(message, displayProvider(creds), displayModel(creds), null, null, null, null, null);
        }
    }

    private AgentDtos.ChatReply complete(AgentCredentials creds, String system, String contextLabel, String context,
                                        ParsedChat parsed, AgentDtos.ChatReply fakeReply, AgentDtos.FitView fit,
                                        List<AgentDtos.FitView> fits, AgentDtos.FitProposalView proposal,
                                        AgentDtos.AnalysisTemplateView analysisTemplate, AgentDtos.ChartView chart) {
        String provider = displayProvider(creds);
        String model = displayModel(creds);
        if ("fake".equalsIgnoreCase(provider)) {
            if (fit != null || proposal != null || analysisTemplate != null || chart != null
                    || (fits != null && !fits.isEmpty())) {
                return new AgentDtos.ChatReply(fakeReply.reply(), fakeReply.provider(), fakeReply.model(),
                        fit, fits, proposal, analysisTemplate, chart);
            }
            return fakeReply;
        }
        if (blank(creds.baseUrl()) || blank(creds.apiKey()) || blank(model)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MODEL_PROVIDER_UNAVAILABLE",
                    "llm 配置不完整，请检查 base_url (OpenAI)、api_key 和 model");
        }
        String reply = completeOpenAiCompatible(creds, system, contextLabel, context, parsed.history(), parsed.message(), model);
        return new AgentDtos.ChatReply(reply, provider, model, fit, fits, proposal, analysisTemplate, chart);
    }

    private ParsedChat parseRequest(AgentDtos.ChatRequest request) {
        String message = request.message() == null ? "" : request.message().trim();
        if (message.isEmpty()) throw invalid("message is required");
        if (message.length() > 2000) throw invalid("message exceeds 2000 characters");
        return new ParsedChat(message, normalizeHistory(request.history()));
    }

    private AgentDtos.ChatReply fakeRecordReply(Map<String, Object> record, String message, AgentCredentials creds) {
        String code = String.valueOf(record.get("code"));
        String title = String.valueOf(record.get("title"));
        return new AgentDtos.ChatReply(
                "【本地 fake 回复】我已读取记录 " + code + "《" + title + "》。你问的是：" + limit(message, 200)
                        + "。当前状态为 " + record.get("status") + "。真实模型未启用时仅返回确定性演示答复。",
                "fake",
                displayModel(creds),
                null,
                null,
                null,
                null,
                null);
    }

    private AgentDtos.ChatReply fakeProposalReply(Map<String, Object> project, AgentDtos.FitProposalView proposal,
                                                 AgentCredentials creds) {
        String mode;
        if (Boolean.TRUE.equals(proposal.multivariate())) {
            mode = "多元线性回归";
        } else if (proposal.autoCompare()) {
            mode = "多模型比选（" + String.join("/", proposal.candidateIds() == null ? List.of() : proposal.candidateIds()) + "）";
        } else {
            mode = "单方程 " + proposal.equation();
        }
        String reply = "【本地 fake 回复】已为项目《" + project.get("name") + "》拟定拟合方案，请确认后再执行。"
                + "模式：" + mode
                + "；x=" + proposal.xSpec() + "，y=" + proposal.ySpec()
                + "；来源=" + proposal.pointSource()
                + (proposal.csvNameHint() == null ? "" : "；csv=" + proposal.csvNameHint())
                + (Boolean.TRUE.equals(proposal.timeToMinutes()) ? "；时间列将转换为相对分钟" : "")
                + "。" + (proposal.rationale() == null ? "" : proposal.rationale())
                + " 点击「确认拟合」继续。";
        return new AgentDtos.ChatReply(reply, "fake", displayModel(creds), null, null, proposal, null, null);
    }

    private AgentDtos.ChatReply fakeChartReply(Map<String, Object> project, AgentDtos.ChartView chart,
                                              AgentCredentials creds) {
        int n = chart.series() == null || chart.series().isEmpty() || chart.series().get(0).points() == null
                ? 0 : chart.series().get(0).points().size();
        String reply = "【本地 fake 回复】已根据项目《" + project.get("name") + "》中的真实数据生成"
                + chart.title() + "（" + n + " 个数据点）。图表由抽取引擎计算，未由模型编造数值。";
        return new AgentDtos.ChatReply(reply, "fake", displayModel(creds), null, null, null, null, chart);
    }

    private AgentDtos.ChatReply fakeAnalysisReply(Map<String, Object> project, AgentDtos.AnalysisTemplateView template,
                                                 AgentCredentials creds) {
        StringBuilder reply = new StringBuilder();
        reply.append("【本地 fake 回复】已启用通用分析模板「").append(template.label()).append("」（id=")
                .append(template.id()).append("），用于项目《").append(project.get("name")).append("》。\n");
        reply.append(template.description()).append('\n');
        reply.append("请按下列章节组织结论；动力学方程仅为解释框架，不会求解 ODE，也不会编造 μ_max/Ks 等参数。\n");
        reply.append("建议章节：").append(String.join("；", template.outputSections())).append('\n');
        if (template.suggestedFits() != null && !template.suggestedFits().isEmpty()) {
            reply.append("建议先做证据拟合：");
            for (AgentDtos.SuggestedEvidenceFitView fit : template.suggestedFits()) {
                reply.append(fit.purpose()).append("(x=").append(fit.xHint()).append(", y=").append(fit.yHint()).append(")；");
            }
            reply.append('\n');
        }
        reply.append("缺测项请写入分析边界：").append(String.join("、", template.uncertaintyChecklist()));
        return new AgentDtos.ChatReply(reply.toString(), "fake", displayModel(creds), null, null, null, template, null);
    }

    private AgentDtos.ChatReply fakeProjectReply(Map<String, Object> project, String message, AgentDtos.FitView fit,
                                                AgentCredentials creds) {
        return fakeProjectReply(project, message, fit, fit == null ? null : List.of(fit), creds);
    }

    private AgentDtos.ChatReply fakeProjectReply(Map<String, Object> project, String message,
                                                AgentDtos.FitView fit, List<AgentDtos.FitView> fits,
                                                AgentCredentials creds) {
        String reply;
        if (fit != null) {
            int count = fits == null ? 1 : fits.size();
            reply = "【本地 fake 回复】已完成项目《" + project.get("name") + "》的确定性拟合（共 " + count + " 个结果）。"
                    + "首个方程 " + fit.equation() + "，n=" + fit.n()
                    + "，R²=" + String.format("%.4f", fit.rSquared())
                    + "，RMSE=" + String.format("%.4f", fit.rmse())
                    + "。参数：" + fit.parameters() + "。以下数值来自拟合引擎，未由模型编造。";
        } else {
            reply = "【本地 fake 回复】我已读取项目《" + project.get("name") + "》。你问的是：" + limit(message, 200)
                    + "。项目状态为 " + project.get("status") + "。真实模型未启用时仅返回确定性演示答复。";
        }
        return new AgentDtos.ChatReply(reply, "fake", displayModel(creds), fit, fits, null, null, charts.fromFit(fit));
    }

    private AgentDtos.AnalysisTemplateView toAnalysisTemplateView(AnalysisTemplateCatalog.Template template) {
        List<AgentDtos.SuggestedEvidenceFitView> suggested = template.suggestedFits().stream()
                .map(s -> new AgentDtos.SuggestedEvidenceFitView(s.purpose(), s.xHint(), s.yHint(), s.note()))
                .toList();
        return new AgentDtos.AnalysisTemplateView(
                template.id(),
                template.label(),
                template.description(),
                template.outputSections(),
                template.uncertaintyChecklist(),
                suggested
        );
    }

    private AgentDtos.FitView toFitView(FitModels.FitResult result) {
        List<AgentDtos.FitSkipView> skipped = result.skipped().stream()
                .map(s -> new AgentDtos.FitSkipView(s.recordCode(), s.reason()))
                .toList();
        List<AgentDtos.FitCurvePoint> curve = result.curveSample().stream()
                .map(p -> new AgentDtos.FitCurvePoint(
                        ((Number) p.get("x")).doubleValue(),
                        ((Number) p.get("y")).doubleValue()))
                .toList();
        List<AgentDtos.FitPointView> points = result.points().stream()
                .map(p -> new AgentDtos.FitPointView(
                        ((Number) p.get("x")).doubleValue(),
                        ((Number) p.get("y")).doubleValue(),
                        String.valueOf(p.get("recordCode")),
                        String.valueOf(p.get("source"))))
                .toList();
        List<AgentDtos.FitComparisonView> comparisons = result.comparisons().stream()
                .map(c -> new AgentDtos.FitComparisonView(
                        c.equation(),
                        Double.isFinite(c.rSquared()) ? c.rSquared() : null,
                        Double.isFinite(c.rmse()) ? c.rmse() : null,
                        c.n(),
                        c.selected()))
                .toList();
        return new AgentDtos.FitView(result.equation(), result.parameters(), result.rSquared(), result.rmse(),
                result.n(), result.usedRecordCodes(), skipped, curve, points, comparisons);
    }

    private AgentDtos.FitProposalView toProposalView(FitModels.FitProposal proposal) {
        return new AgentDtos.FitProposalView(
                proposal.equation(),
                proposal.autoCompare(),
                proposal.candidateIds() == null ? List.of() : proposal.candidateIds(),
                proposal.xSpec(),
                proposal.ySpec(),
                proposal.pointSource(),
                proposal.csvNameHint(),
                proposal.recordCodes() == null ? List.of() : proposal.recordCodes(),
                proposal.statuses() == null ? List.of() : proposal.statuses(),
                proposal.experimentType(),
                proposal.keyword(),
                proposal.rationale(),
                proposal.multivariate(),
                proposal.timeToMinutes()
        );
    }

    private FitModels.FitProposal fromProposalView(AgentDtos.FitProposalView view) {
        // Attachment fits: ignore stale record/status filters copied from older proposal cards.
        List<String> codes = List.of();
        List<String> statuses = List.of();
        if ("TEMPLATE_FIELDS".equalsIgnoreCase(view.pointSource())) {
            codes = view.recordCodes() == null ? List.of() : view.recordCodes();
            statuses = view.statuses() == null ? List.of() : view.statuses();
        }
        return new FitModels.FitProposal(
                true,
                view.equation(),
                view.autoCompare(),
                view.candidateIds() == null ? List.of() : view.candidateIds(),
                view.xSpec(),
                view.ySpec(),
                view.pointSource() == null ? "AUTO" : view.pointSource(),
                view.csvNameHint(),
                codes,
                statuses,
                null,
                view.keyword(),
                view.rationale(),
                null,
                Boolean.TRUE.equals(view.multivariate())
                        || CurveFitService.splitSpecs(view.xSpec()).size() > 1
                        || CurveFitService.looksLikeMultivariateEquation(view.equation()),
                Boolean.TRUE.equals(view.timeToMinutes())
                        || (view.xSpec() != null && view.xSpec().contains("时间"))
        );
    }

    private boolean isConfirmPhrase(String message) {
        if (message == null) return false;
        String text = message.trim().toLowerCase(Locale.ROOT);
        return text.equals("确认") || text.equals("确认拟合") || text.equals("用这个")
                || text.equals("按方案拟合") || text.contains("确认按拟定方案") || text.contains("进行拟合");
    }

    /** Keep recent user turns so short replies like「对数」still inherit 自变量/因变量 mapping. */
    private String recentUserHints(List<AgentDtos.ChatMessage> history) {
        if (history == null || history.isEmpty()) return "";
        StringBuilder text = new StringBuilder();
        int count = 0;
        for (int i = history.size() - 1; i >= 0 && count < 6; i--) {
            AgentDtos.ChatMessage item = history.get(i);
            if (item == null || !"user".equalsIgnoreCase(item.role())) continue;
            String content = item.content() == null ? "" : item.content().trim();
            if (content.isEmpty()) continue;
            if (text.length() > 0) text.insert(0, '\n');
            text.insert(0, content);
            count++;
        }
        return text.toString();
    }

    private String displayProvider(AgentCredentials creds) {
        if (creds == null || blank(creds.provider())) return "fake";
        return creds.provider();
    }

    private String displayModel(AgentCredentials creds) {
        if (creds == null || blank(creds.model())) return "fake-deterministic-v1";
        return creds.model();
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String completeOpenAiCompatible(AgentCredentials creds, String system, String contextLabel, String context,
                                            List<AgentDtos.ChatMessage> history, String message, String model) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", system + "\n\n" + contextLabel + ":\n" + context));
        for (AgentDtos.ChatMessage item : history) {
            messages.add(Map.of("role", item.role(), "content", item.content()));
        }
        messages.add(Map.of("role", "user", "content", message));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", 0);
        body.put("max_tokens", Math.max(256, Math.min(4000, properties.getMaxOutputTokens())));
        if (model.toLowerCase().startsWith("deepseek")) body.put("thinking", Map.of("type", "disabled"));
        RestClient client = restClientFor(creds);
        try {
            JsonNode root = client.post()
                    .uri("chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + creds.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            String content = contentText(root == null ? null : root.path("choices").path(0).path("message").path("content"));
            if (content == null || content.isBlank()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "MODEL_PROVIDER_ERROR", "Model returned empty chat content");
            }
            return content.trim();
        } catch (ApiException e) {
            throw e;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String code = status == 429 ? "AGENT_RATE_LIMITED"
                    : status == 401 || status == 403 || status >= 500 ? "MODEL_PROVIDER_UNAVAILABLE"
                    : "MODEL_PROVIDER_ERROR";
            String detail = status == 401 || status == 403
                    ? "llm 中的 API Key 无效或权限不足（HTTP " + status + "）"
                    : "Model provider request failed with status " + status;
            throw new ApiException(status == 429 ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_GATEWAY, code, detail);
        } catch (ResourceAccessException e) {
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "AGENT_TIMEOUT", "Model provider timed out");
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "MODEL_PROVIDER_ERROR",
                    "Model provider response could not be processed: " + e.getMessage());
        }
    }

    private RestClient restClientFor(AgentCredentials creds) {
        String base = blank(creds.baseUrl()) ? "http://127.0.0.1/" : creds.baseUrl().replaceAll("/*$", "/");
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000, properties.getTimeoutMs())))
                .build());
        factory.setReadTimeout(Duration.ofMillis(Math.max(1000, properties.getTimeoutMs())));
        return RestClient.builder().baseUrl(base).requestFactory(factory).build();
    }

    private String buildRecordContext(Map<String, Object> record) {
        StringBuilder text = new StringBuilder();
        text.append("code=").append(record.get("code")).append('\n');
        text.append("title=").append(record.get("title")).append('\n');
        text.append("experimentType=").append(record.get("experiment_type")).append('\n');
        text.append("experimentDate=").append(record.get("experiment_date")).append('\n');
        text.append("status=").append(record.get("status")).append('\n');
        text.append("currentRevisionNo=").append(record.get("current_revision_no")).append('\n');
        text.append("purpose=").append(limit(String.valueOf(record.get("purpose")), 1500)).append('\n');
        text.append("fieldValues=").append(limit(String.valueOf(record.get("field_values_json")), 2500)).append('\n');
        text.append("contentPlainText=").append(limit(String.valueOf(record.get("content_plain_text")), 3000)).append('\n');
        List<Map<String,Object>> artifacts=contextStore.latestRecordArtifact(UUID.fromString(record.get("id").toString()));
        appendLatestArtifact(text, artifacts);
        return text.toString();
    }

    private String buildProjectContext(Map<String, Object> project) {
        String projectId = project.get("id").toString();
        StringBuilder text = new StringBuilder();
        text.append("projectId=").append(projectId).append('\n');
        text.append("name=").append(project.get("name")).append('\n');
        text.append("status=").append(project.get("status")).append('\n');
        text.append("description=").append(limit(String.valueOf(project.get("description")), 1000)).append('\n');
        text.append("detailedDescription=").append(limit(String.valueOf(project.get("detailed_description")), 2000)).append('\n');
        UUID projectUuid=UUID.fromString(projectId);long members=contextStore.memberCount(projectUuid);
        text.append("memberCount=").append(members).append('\n');
        List<Map<String,Object>> roles=contextStore.memberRoleCounts(projectUuid);
        text.append("membersByRole=");
        for (Map<String, Object> row : roles) text.append(row.get("role")).append('=').append(row.get("cnt")).append(';');
        text.append('\n');
        List<Map<String,Object>> statuses=contextStore.recordStatusCounts(projectUuid);
        text.append("recordsByStatus=");
        for (Map<String, Object> row : statuses) text.append(row.get("status")).append('=').append(row.get("cnt")).append(';');
        text.append('\n');
        List<Map<String,Object>> records=contextStore.recentRecords(projectUuid);
        text.append("recentRecords:\n");
        for (Map<String, Object> row : records) {
            text.append("- ").append(row.get("code")).append(" | ").append(limit(String.valueOf(row.get("title")), 120))
                    .append(" | ").append(row.get("status")).append(" | R").append(row.get("current_revision_no")).append('\n');
        }
        List<Map<String,Object>> artifacts=contextStore.latestProjectArtifact(projectUuid);
        appendLatestArtifact(text, artifacts);
        return text.toString();
    }

    private void appendLatestArtifact(StringBuilder text, List<Map<String, Object>> artifacts) {
        if (artifacts.isEmpty()) return;
        try {
            JsonNode content = json.readTree(String.valueOf(artifacts.get(0).get("content_json")));
            text.append("latestSummaryHeadline=").append(limit(content.path("headline").asText(""), 300)).append('\n');
            text.append("latestExecutiveSummary=").append(limit(content.path("executiveSummary").asText(""), 800)).append('\n');
        } catch (Exception ignored) {
            // context enrichment is best-effort
        }
    }

    private List<AgentDtos.ChatMessage> normalizeHistory(List<AgentDtos.ChatMessage> history) {
        if (history == null || history.isEmpty()) return List.of();
        if (history.size() > 20) throw invalid("history exceeds 20 messages");
        List<AgentDtos.ChatMessage> values = new ArrayList<>();
        for (AgentDtos.ChatMessage item : history) {
            if (item == null) continue;
            String role = item.role() == null ? "" : item.role().trim();
            String content = item.content() == null ? "" : item.content().trim();
            if (!"user".equals(role) && !"assistant".equals(role)) throw invalid("history role must be user or assistant");
            if (content.isEmpty()) continue;
            if (content.length() > 4000) throw invalid("history content exceeds 4000 characters");
            values.add(new AgentDtos.ChatMessage(role, content));
        }
        if (values.size() > 20) throw invalid("history exceeds 20 messages");
        return values;
    }

    private Map<String, Object> requireVisibleRecord(UUID actor, UUID recordId) {
        return contextStore.findVisibleRecord(actor,recordId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","Resource not found or inaccessible"));
    }

    private Map<String, Object> requireVisibleProject(UUID actor, UUID projectId) {
        return contextStore.findVisibleProject(actor,projectId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","Resource not found or inaccessible"));
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            log.warn("Agent chat request rejected: agent.enabled=false (set AGENT_ENABLED=true to enable)");
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AGENT_DISABLED", "Agent functionality is disabled");
        }
    }

    private String contentText(JsonNode content) {
        if (content == null || content.isNull() || content.isMissingNode()) return null;
        if (content.isTextual()) return content.asText();
        if (content.isArray()) {
            StringBuilder text = new StringBuilder();
            for (JsonNode part : content) {
                if (part.isTextual()) text.append(part.asText());
                else if (part.hasNonNull("text")) text.append(part.path("text").asText());
            }
            return text.toString();
        }
        return content.toString();
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "AGENT_INVALID_REQUEST", message);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String limit(String value, int max) {
        if (value == null || "null".equals(value)) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record ParsedChat(String message, List<AgentDtos.ChatMessage> history) {}
}
