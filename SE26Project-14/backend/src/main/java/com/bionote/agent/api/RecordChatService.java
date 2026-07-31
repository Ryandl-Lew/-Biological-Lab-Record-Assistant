package com.bionote.agent.api;

import com.bionote.agent.config.AgentCredentialService;
import com.bionote.agent.config.AgentCredentials;
import com.bionote.agent.config.AgentProperties;
import com.bionote.agent.fit.ChartPlotService;
import com.bionote.agent.fit.CurveFitEngine;
import com.bionote.agent.fit.CurveFitService;
import com.bionote.agent.fit.ExpressionParser;
import com.bionote.agent.fit.FitMethodCatalog;
import com.bionote.agent.fit.FitModels;
import com.bionote.agent.fit.FitProposalResolver;
import com.bionote.agent.tool.AgentToolContext;
import com.bionote.agent.tool.AgentToolRegistry;
import com.bionote.agent.tool.bionote.BioNoteAgentReadService;
import com.bionote.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

@Service
public class RecordChatService implements AgentChatUseCase {
    private static final String RECORD_SYSTEM =
            """
            You are BioNote's read-only assistant for one experiment record.
            Answer from the provided record context, chat history, and available tools.
            Available tools:
            - `list_record_attachments(recordId)`: list all attachments for this record
            - `read_attachment_content(attachmentId, recordId?)`: read full text of a text attachment (CSV/TXT/MD/XLSX)
            When the user asks about file contents, call `read_attachment_content` with the attachment ID (the `id=` field from the attachments list).
            If facts are missing or tools return errors, say you do not know. Never invent experimental outcomes, reviews, or revision numbers.
            Never claim you modified the record, reviews, attachments, or membership.
            Never output email addresses, secrets, storage paths, or hidden reasoning.
            Reply in the user's language. Keep answers concise and practical.
            """;

    private static final String PROJECT_SYSTEM =
            """
            You are BioNote's read-only assistant for one collaboration project.
            Use the provided tools to explore data, read files, plot charts, and fit curves.

            Available tools:
            - list_project_attachments: list all project attachments
            - list_record_attachments(recordId): list attachments for a record
            - read_attachment_content(filename/referenceId): read CSV/TXT/MD/XLSX content
            - plot_chart(chartType, xColumn, yColumn): generate a chart from attachment data
            - fit_data(xColumn, yColumn, equation?, autoCompare?): fit a curve to attachment data

            CRITICAL RULES:
            1. Data comes from project record attachments or temporary CHAT_FILE_REFERENCES explicitly attached to the current message.
               Use list_project_attachments for record files. For a CHAT_FILE_REFERENCE, pass its referenceId to read/plot/fit tools.
            2. NEVER invent regression coefficients, R², RMSE, equations, or fit parameters.
               These values MUST come from tool results.
            3. NEVER output markdown chart syntax (![title](...)) or fit result tables.
               The UI renders charts and fit cards automatically from tool results.
            4. When the user asks to fit/analyze data, you MUST call the fit_data tool.
               Do NOT attempt to calculate or estimate anything from the context preview.
            5. If xColumn/yColumn are unclear, ask the user to specify them first.
            6. Reply in the user's language. Keep responses brief and factual.
               Only describe analysis results when tools have provided actual data.
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
    private final BioNoteAgentReadService agentReads;
    private final AgentToolRegistry toolRegistry;
    private final ChatSessionStore sessions;

    public RecordChatService(
            AgentProperties properties,
            AgentCredentialService credentials,
            AgentChatContextStore contextStore,
            ObjectMapper json,
            CurveFitService curveFits,
            FitProposalResolver proposals,
            AgentChatReferenceService chatReferences,
            ChartPlotService charts,
            BioNoteAgentReadService agentReads,
            AgentToolRegistry toolRegistry,
            ChatSessionStore sessions) {
        this.properties = properties;
        this.credentials = credentials;
        this.contextStore = contextStore;
        this.json = json;
        this.curveFits = curveFits;
        this.proposals = proposals;
        this.chatReferences = chatReferences;
        this.charts = charts;
        this.agentReads = agentReads;
        this.toolRegistry = toolRegistry;
        this.sessions = sessions;
    }

    public AgentDtos.ChatReply chat(UUID actor, UUID recordId, AgentDtos.ChatRequest request) {
        requireEnabled();
        Map<String, Object> record = requireVisibleRecord(actor, recordId);
        ParsedChat parsed = parseRequest(request);
        String context = buildRecordContext(record);

        AgentCredentials creds = credentials.resolve(actor);
        return complete(
                creds,
                RECORD_SYSTEM,
                "RECORD_CONTEXT",
                context,
                parsed,
                fakeRecordReply(record, parsed.message(), creds),
                null,
                null,
                null,
                null,
                null,
                actor,
                UUID.fromString(String.valueOf(record.get("project_id"))),
                recordId,
                references(request));
    }

    public AgentDtos.ChatReply chatAboutProject(
            UUID actor, UUID projectId, AgentDtos.ChatRequest request) {
        requireEnabled();
        Map<String, Object> project = requireVisibleProject(actor, projectId);
        ParsedChat parsed = parseRequest(request);
        String context = buildProjectContext(project);
        String referenceContext =
                chatReferences.formatForContext(actor, projectId, references(request));
        if (!referenceContext.isBlank()) context += "\n\n" + referenceContext;
        List<FitModels.CatalogRecord> catalog = curveFits.buildFitCatalog(projectId);
        String catalogText = curveFits.formatFitCatalog(catalog);
        context =
                context
                        + "\n\nFIT_DATA_CATALOG:\n"
                        + catalogText
                        + "\nEQUATION_CATALOG:\n"
                        + FitMethodCatalog.catalogDescription();

        if (request.fitConfirm() != null) {
            return executeConfirmedFit(
                    actor,
                    projectId,
                    project,
                    parsed,
                    context,
                    request.fitConfirm(),
                    references(request));
        }

        AgentCredentials creds = credentials.resolve(actor);
        if ("fake".equalsIgnoreCase(displayProvider(creds))) {
            FitModels.FitIntent intent = curveFits.parseIntent(parsed.message());
            if (intent.fitRequested()) {
                AgentDtos.FitProposalView proposal =
                        toProposalView(proposals.resolve(parsed.message(), catalogText, intent, creds));
                return fakeProposalReply(project, proposal, creds);
            }
        }

        AgentDtos.ChatReply fake = fakeProjectReply(project, parsed.message(), null, creds);
        return complete(
                creds,
                PROJECT_SYSTEM,
                "PROJECT_CONTEXT",
                context,
                parsed,
                fake,
                null,
                null,
                null,
                null,
                null,
                actor,
                projectId,
                null,
                references(request));
    }

    private AgentDtos.ChatReply executeConfirmedFit(
            UUID actor,
            UUID projectId,
            Map<String, Object> project,
            ParsedChat parsed,
            String context,
            AgentDtos.FitProposalView confirm,
            List<UUID> referenceIds) {
        AgentCredentials creds = credentials.resolve(actor);
        FitModels.FitProposal proposal = fromProposalView(confirm);
        try {
            List<FitModels.FitResult> results =
                    curveFits.fitProposalAll(actor, projectId, proposal);
            List<AgentDtos.FitView> fitViews = results.stream().map(this::toFitView).toList();
            AgentDtos.FitView primary = fitViews.isEmpty() ? null : fitViews.get(0);
            AgentDtos.ChartView chart = charts.fromFit(primary);
            String fitContext =
                    "\n\nFIT_RESULTS:\n"
                            + write(fitViews)
                            + (chart == null ? "" : "\n\nCHART:\n" + write(chart));
            AgentDtos.ChatReply fake =
                    fakeProjectReply(project, parsed.message(), primary, fitViews, creds);
            return complete(
                    creds,
                    PROJECT_SYSTEM,
                    "PROJECT_CONTEXT",
                    context + fitContext,
                    parsed,
                    fake,
                    primary,
                    fitViews,
                    null,
                    null,
                    chart,
                    actor,
                    projectId,
                    null,
                    referenceIds);
        } catch (ApiException e) {
            String message = e.getMessage() == null ? "拟合失败" : e.getMessage();
            if ("invalid number".equalsIgnoreCase(message)
                    || message.toLowerCase(Locale.ROOT).contains("invalid number")) {
                message = "数据或方程无法解析为数值。若时间列为时刻，请说明“转换为分钟”；多元回归请用列名列表而非 y=a+b1*x1+... 省略式。";
            }
            return new AgentDtos.ChatReply(
                    message,
                    displayProvider(creds),
                    displayModel(creds),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
        }
    }

    private AgentDtos.ChatReply complete(
            AgentCredentials creds,
            String system,
            String contextLabel,
            String context,
            ParsedChat parsed,
            AgentDtos.ChatReply fakeReply,
            AgentDtos.FitView fit,
            List<AgentDtos.FitView> fits,
            AgentDtos.FitProposalView proposal,
            AgentDtos.AnalysisTemplateView analysisTemplate,
            AgentDtos.ChartView chart) {
        return complete(
                creds,
                system,
                contextLabel,
                context,
                parsed,
                fakeReply,
                fit,
                fits,
                proposal,
                analysisTemplate,
                chart,
                null,
                null,
                null,
                List.of());
    }

    private AgentDtos.ChatReply complete(
            AgentCredentials creds,
            String system,
            String contextLabel,
            String context,
            ParsedChat parsed,
            AgentDtos.ChatReply fakeReply,
            AgentDtos.FitView fit,
            List<AgentDtos.FitView> fits,
            AgentDtos.FitProposalView proposal,
            AgentDtos.AnalysisTemplateView analysisTemplate,
            AgentDtos.ChartView chart,
            UUID actor,
            UUID projectId,
            UUID recordId,
            List<UUID> referenceIds) {
        String provider = displayProvider(creds);
        String model = displayModel(creds);
        if ("fake".equalsIgnoreCase(provider)) {
            if (fit != null
                    || proposal != null
                    || analysisTemplate != null
                    || chart != null
                    || (fits != null && !fits.isEmpty())) {
                return new AgentDtos.ChatReply(
                        fakeReply.reply(),
                        fakeReply.provider(),
                        fakeReply.model(),
                        fit,
                        fits,
                        proposal,
                        analysisTemplate,
                        chart,
                        null);
            }
            return fakeReply;
        }
        if (blank(creds.baseUrl()) || blank(creds.apiKey()) || blank(model)) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "MODEL_PROVIDER_UNAVAILABLE",
                    "llm 配置不完整，请检查 base_url (OpenAI)、api_key 和 model");
        }
        CompletionResult result;
        try {
            result =
                    completeOpenAiCompatible(
                            creds,
                            system,
                            contextLabel,
                            context,
                            parsed.history(),
                            parsed.message(),
                            model,
                            actor,
                            projectId,
                            recordId,
                            referenceIds);
        } catch (ApiException e) {
            log.warn(
                    "LLM chat recovered with deterministic reply: code={} message={}",
                    e.code(),
                    e.getMessage());
            return new AgentDtos.ChatReply(
                    resilientReply(fakeReply.reply()),
                    provider,
                    model,
                    fit,
                    fits,
                    proposal,
                    analysisTemplate,
                    chart,
                    null);
        }
        return new AgentDtos.ChatReply(
                result.reply,
                provider,
                model,
                result.fit != null ? result.fit : fit,
                result.fits != null ? result.fits : fits,
                proposal,
                analysisTemplate,
                result.chart != null ? result.chart : chart,
                result.systemError);
    }

    private ParsedChat parseRequest(AgentDtos.ChatRequest request) {
        String message = request.message() == null ? "" : request.message().trim();
        if (message.isEmpty()) throw invalid("message is required");
        if (message.length() > 2000) throw invalid("message exceeds 2000 characters");
        return new ParsedChat(message, normalizeHistory(request.history()));
    }

    private List<UUID> references(AgentDtos.ChatRequest request) {
        if (request == null || request.referenceIds() == null) return List.of();
        return request.referenceIds().stream().filter(java.util.Objects::nonNull).distinct().limit(5).toList();
    }

    private String resilientReply(String value) {
        if (value == null || value.isBlank()) return "已收到问题，请结合当前页面数据继续查看。";
        return value.replace("【本地 fake 回复】", "")
                .replace("真实模型未启用时仅返回确定性演示答复。", "")
                .trim();
    }

    private AgentDtos.ChatReply fakeRecordReply(
            Map<String, Object> record, String message, AgentCredentials creds) {
        String code = String.valueOf(record.get("code"));
        String title = String.valueOf(record.get("title"));
        return new AgentDtos.ChatReply(
                "【本地 fake 回复】我已读取记录 "
                        + code
                        + "《"
                        + title
                        + "》。你问的是："
                        + limit(message, 200)
                        + "。当前状态为 "
                        + record.get("status")
                        + "。真实模型未启用时仅返回确定性演示答复。",
                "fake",
                displayModel(creds),
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private AgentDtos.ChatReply fakeProposalReply(
            Map<String, Object> project,
            AgentDtos.FitProposalView proposal,
            AgentCredentials creds) {
        String mode;
        if (Boolean.TRUE.equals(proposal.multivariate())) {
            mode = "多元线性回归";
        } else if (proposal.autoCompare()) {
            mode =
                    "多模型比选（"
                            + String.join(
                                    "/",
                                    proposal.candidateIds() == null
                                            ? List.of()
                                            : proposal.candidateIds())
                            + "）";
        } else {
            mode = "单方程 " + proposal.equation();
        }
        String reply =
                "【本地 fake 回复】已为项目《"
                        + project.get("name")
                        + "》拟定拟合方案，请确认后再执行。"
                        + "模式："
                        + mode
                        + "；x="
                        + proposal.xSpec()
                        + "，y="
                        + proposal.ySpec()
                        + "；来源="
                        + proposal.pointSource()
                        + (proposal.csvNameHint() == null ? "" : "；csv=" + proposal.csvNameHint())
                        + (Boolean.TRUE.equals(proposal.timeToMinutes()) ? "；时间列将转换为相对分钟" : "")
                        + "。"
                        + (proposal.rationale() == null ? "" : proposal.rationale())
                        + " 点击「确认拟合」继续。";
        return new AgentDtos.ChatReply(
                reply, "fake", displayModel(creds), null, null, proposal, null, null, null);
    }

    private AgentDtos.ChatReply fakeChartReply(
            Map<String, Object> project, AgentDtos.ChartView chart, AgentCredentials creds) {
        int n =
                chart.series() == null
                                || chart.series().isEmpty()
                                || chart.series().get(0).points() == null
                        ? 0
                        : chart.series().get(0).points().size();
        String reply =
                "【本地 fake 回复】已根据项目《"
                        + project.get("name")
                        + "》中的真实数据生成"
                        + chart.title()
                        + "（"
                        + n
                        + " 个数据点）。图表由抽取引擎计算，未由模型编造数值。";
        return new AgentDtos.ChatReply(
                reply, "fake", displayModel(creds), null, null, null, null, chart, null);
    }

    private AgentDtos.ChatReply fakeProjectReply(
            Map<String, Object> project,
            String message,
            AgentDtos.FitView fit,
            AgentCredentials creds) {
        return fakeProjectReply(project, message, fit, fit == null ? null : List.of(fit), creds);
    }

    private AgentDtos.ChatReply fakeProjectReply(
            Map<String, Object> project,
            String message,
            AgentDtos.FitView fit,
            List<AgentDtos.FitView> fits,
            AgentCredentials creds) {
        String reply;
        if (fit != null) {
            int count = fits == null ? 1 : fits.size();
            reply =
                    "【本地 fake 回复】已完成项目《"
                            + project.get("name")
                            + "》的确定性拟合（共 "
                            + count
                            + " 个结果）。"
                            + "首个方程 "
                            + fit.equation()
                            + "，n="
                            + fit.n()
                            + "，R²="
                            + String.format("%.4f", fit.rSquared())
                            + "，RMSE="
                            + String.format("%.4f", fit.rmse())
                            + "。参数："
                            + fit.parameters()
                            + "。以下数值来自拟合引擎，未由模型编造。";
        } else {
            reply =
                    "【本地 fake 回复】我已读取项目《"
                            + project.get("name")
                            + "》。你问的是："
                            + limit(message, 200)
                            + "。项目状态为 "
                            + project.get("status")
                            + "。真实模型未启用时仅返回确定性演示答复。";
        }
        return new AgentDtos.ChatReply(
                reply,
                "fake",
                displayModel(creds),
                fit,
                fits,
                null,
                null,
                charts.fromFit(fit),
                null);
    }

    private AgentDtos.FitView toFitView(FitModels.FitResult result) {
        List<AgentDtos.FitSkipView> skipped =
                result.skipped().stream()
                        .map(s -> new AgentDtos.FitSkipView(s.recordCode(), s.reason()))
                        .toList();
        List<AgentDtos.FitCurvePoint> curve =
                result.curveSample().stream()
                        .map(
                                p ->
                                        new AgentDtos.FitCurvePoint(
                                                ((Number) p.get("x")).doubleValue(),
                                                ((Number) p.get("y")).doubleValue()))
                        .toList();
        List<AgentDtos.FitPointView> points =
                result.points().stream()
                        .map(
                                p ->
                                        new AgentDtos.FitPointView(
                                                ((Number) p.get("x")).doubleValue(),
                                                ((Number) p.get("y")).doubleValue(),
                                                String.valueOf(p.get("recordCode")),
                                                String.valueOf(p.get("source"))))
                        .toList();
        List<AgentDtos.FitComparisonView> comparisons =
                result.comparisons().stream()
                        .map(
                                c ->
                                        new AgentDtos.FitComparisonView(
                                                c.equation(),
                                                Double.isFinite(c.rSquared()) ? c.rSquared() : null,
                                                Double.isFinite(c.rmse()) ? c.rmse() : null,
                                                c.n(),
                                                c.selected()))
                        .toList();
        return new AgentDtos.FitView(
                result.equation(),
                result.parameters(),
                result.rSquared(),
                result.rmse(),
                result.n(),
                result.usedRecordCodes(),
                skipped,
                curve,
                points,
                comparisons);
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
                proposal.timeToMinutes());
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
                FitMethodCatalog.normalizeEquation(view.equation()),
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
                        || (view.xSpec() != null && view.xSpec().contains("时间")));
    }

    private boolean isConfirmPhrase(String message) {
        if (message == null) return false;
        String text = message.trim().toLowerCase(Locale.ROOT);
        return text.equals("确认")
                || text.equals("确认拟合")
                || text.equals("用这个")
                || text.equals("按方案拟合")
                || text.contains("确认按拟定方案")
                || text.contains("进行拟合");
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

    private CompletionResult completeOpenAiCompatible(
            AgentCredentials creds,
            String system,
            String contextLabel,
            String context,
            List<AgentDtos.ChatMessage> history,
            String message,
            String model,
            UUID actor,
            UUID projectId,
            UUID recordId,
            List<UUID> referenceIds) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(
                Map.of(
                        "role",
                        "system",
                        "content",
                        system + "\n\n" + contextLabel + ":\n" + context));
        for (AgentDtos.ChatMessage item : history) {
            messages.add(Map.of("role", item.role(), "content", item.content()));
        }
        messages.add(Map.of("role", "user", "content", message));
        boolean hasTools = actor != null && projectId != null;
        String toolError = null;
        AgentDtos.ChartView chartResult = null;
        AgentDtos.FitView fitResult = null;
        List<AgentDtos.FitView> fitsResults = null;
        long deadlineNanos = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        for (int turn = 0; turn < 5; turn++) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("temperature", 0);
            body.put("max_tokens", Math.max(256, Math.min(4000, properties.getMaxOutputTokens())));
            if (hasTools) {
                body.put("tools", chatToolDefinitions());
                body.put("tool_choice", "auto");
            }
            if (model.toLowerCase().startsWith("deepseek"))
                body.put("thinking", Map.of("type", "disabled"));
            log.debug(
                    "LLM chat request: model={} messages={} tools={}",
                    model,
                    messages.size(),
                    hasTools ? 2 : 0);
            if (log.isTraceEnabled()) {
                try {
                    log.trace("LLM chat request body: {}", json.writeValueAsString(body));
                } catch (Exception ignored) {
                }
            }
            long remainingMs = (deadlineNanos - System.nanoTime()) / 1_000_000;
            if (remainingMs <= 0) {
                throw new ApiException(
                        HttpStatus.GATEWAY_TIMEOUT, "AGENT_TIMEOUT", "Model provider timed out");
            }
            RestClient client =
                    restClientFor(
                            creds,
                            Math.max(
                                    1000,
                                    Math.min(properties.getTimeoutMs(), remainingMs)));
            JsonNode root;
            try {
                root =
                        client.post()
                                .uri("chat/completions")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + creds.apiKey())
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(body)
                                .retrieve()
                                .body(JsonNode.class);
            } catch (ApiException e) {
                throw e;
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                String code =
                        status == 429
                                ? "AGENT_RATE_LIMITED"
                                : status == 401 || status == 403 || status >= 500
                                        ? "MODEL_PROVIDER_UNAVAILABLE"
                                        : "MODEL_PROVIDER_ERROR";
                String detail =
                        status == 401 || status == 403
                                ? "llm 中的 API Key 无效或权限不足（HTTP " + status + "）"
                                : "Model provider request failed with status " + status;
                throw new ApiException(
                        status == 429 ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_GATEWAY,
                        code,
                        detail);
            } catch (ResourceAccessException e) {
                throw new ApiException(
                        HttpStatus.GATEWAY_TIMEOUT, "AGENT_TIMEOUT", "Model provider timed out");
            } catch (Exception e) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "MODEL_PROVIDER_ERROR",
                        "Model provider response could not be processed: " + e.getMessage());
            }
            JsonNode usage = root.path("usage");
            log.debug(
                    "LLM chat response: prompt_tokens={} completion_tokens={}",
                    usage.path("prompt_tokens").asLong(0),
                    usage.path("completion_tokens").asLong(0));
            if (log.isTraceEnabled()) {
                try {
                    log.trace("LLM chat response body: {}", json.writeValueAsString(root));
                } catch (Exception ignored) {
                }
            }

            JsonNode choice = root.path("choices").path(0).path("message");
            JsonNode toolCalls = choice.path("tool_calls");
            if (toolCalls.isArray() && toolCalls.size() > 0 && hasTools) {
                messages.add(
                        Map.of(
                                "role",
                                "assistant",
                                "tool_calls",
                                toolCallMessages(toolCalls),
                                "content",
                                ""));
                for (JsonNode call : toolCalls) {
                    String toolName = call.path("function").path("name").asText();
                    String toolId = call.path("id").asText();
                    String result =
                            executeChatTool(
                                    toolName,
                                    call.path("function").path("arguments"),
                                    actor,
                                    projectId,
                                    recordId,
                                    referenceIds);
                    if ("plot_chart".equals(toolName) && result.startsWith("CHART:")) {
                        try {
                            chartResult =
                                    json.readValue(result.substring(6), AgentDtos.ChartView.class);
                        } catch (Exception ignored) {
                        }
                        result = "图表已生成。";
                    }
                    if ("fit_data".equals(toolName) && result.startsWith("FIT:")) {
                        try {
                            @SuppressWarnings("unchecked")
                            List<AgentDtos.FitView> fits =
                                    json.readValue(
                                            result.substring(4),
                                            json.getTypeFactory()
                                                    .constructCollectionType(
                                                            List.class, AgentDtos.FitView.class));
                            fitsResults = fits;
                            fitResult = fits.isEmpty() ? null : fits.get(0);
                            result = buildFitSummary(fits);
                        } catch (Exception ignored) {
                        }
                    }
                    if (result.startsWith("错误:")
                            || result.startsWith("拟合执行失败:")
                            || result.startsWith("未找到可用于")) {
                        if (toolError != null) {
                            return new CompletionResult(
                                    result, toolError, chartResult, fitResult, fitsResults);
                        }
                        toolError = result;
                    }
                    messages.add(Map.of("role", "tool", "tool_call_id", toolId, "content", result));
                }
                continue;
            }

            String content = contentText(choice.path("content"));
            if (content == null || content.isBlank()) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "MODEL_PROVIDER_ERROR",
                        "Model returned empty chat content");
            }
            return new CompletionResult(
                    content.trim(), toolError, chartResult, fitResult, fitsResults);
        }
        if (fitResult != null) {
            return new CompletionResult(
                    buildFitSummary(fitsResults), toolError, chartResult, fitResult, fitsResults);
        }
        if (chartResult != null) {
            return new CompletionResult("图表已根据附件数据生成。", toolError, chartResult, null, null);
        }
        if (toolError != null) return new CompletionResult(toolError, toolError);
        throw new ApiException(
                HttpStatus.BAD_GATEWAY, "MODEL_PROVIDER_ERROR", "Model exceeded tool-call limit");
    }

    private record CompletionResult(
            String reply,
            String systemError,
            AgentDtos.ChartView chart,
            AgentDtos.FitView fit,
            List<AgentDtos.FitView> fits) {
        CompletionResult(String reply, String systemError) {
            this(reply, systemError, null, null, null);
        }
    }

    private static final Set<String> CHAT_TOOL_NAMES =
            Set.of(
                    "list_project_attachments",
                    "list_record_attachments",
                    "read_attachment_content",
                    "plot_chart",
                    "fit_data");
    private static final int TOOL_CONTENT_MAX_CHARS = 4000;

    private List<Map<String, Object>> chatToolDefinitions() {
        return toolRegistry.definitions().stream()
                .filter(d -> CHAT_TOOL_NAMES.contains(d.name()))
                .map(
                        d ->
                                Map.of(
                                        "type",
                                        "function",
                                        "function",
                                        Map.of(
                                                "name", d.name(),
                                                "description", d.description(),
                                                "parameters", convertSchema(d.inputSchema()))))
                .toList();
    }

    private JsonNode convertSchema(JsonNode schema) {
        return schema;
    }

    private JsonNode safeParseArgs(String text) {
        if (text == null || text.isBlank()) return json.createObjectNode();
        try {
            return json.readTree(text);
        } catch (Exception e) {
            return json.createObjectNode();
        }
    }

    private List<Map<String, Object>> toolCallMessages(JsonNode toolCalls) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode call : toolCalls) {
            JsonNode argsNode = call.path("function").path("arguments");
            String argsStr = argsNode.isTextual() ? argsNode.asText() : argsNode.toString();
            result.add(
                    Map.of(
                            "id", call.path("id").asText(),
                            "type", "function",
                            "function",
                                    Map.of(
                                            "name",
                                            call.path("function").path("name").asText(),
                                            "arguments",
                                            argsStr)));
        }
        return result;
    }

    private String executeChatTool(
            String name,
            JsonNode rawArgs,
            UUID actor,
            UUID projectId,
            UUID recordId,
            List<UUID> referenceIds) {
        try {
            AgentToolContext ctx =
                    new AgentToolContext(
                            UUID.randomUUID(),
                            actor,
                            projectId,
                            recordId != null ? recordId : projectId,
                            recordId != null ? "RECORD" : "PROJECT",
                            recordId != null ? recordId : projectId,
                            null,
                            null,
                            () -> false);
            // OpenAI may return function.arguments as a JSON string; normalize to ObjectNode
            JsonNode args = rawArgs.isTextual() ? safeParseArgs(rawArgs.asText()) : rawArgs;
            UUID referenceId = allowedReference(args.path("referenceId").asText(null), referenceIds);
            if ("list_project_attachments".equals(name)) {
                var payload = agentReads.listProjectAttachments(ctx);
                return json.writeValueAsString(payload.data().get("data"));
            }
            if ("list_record_attachments".equals(name)) {
                String requestedRecord = args.path("recordId").asText();
                UUID recId =
                        requestedRecord == null || requestedRecord.isBlank()
                                ? recordId
                                : UUID.fromString(requestedRecord);
                if (recId == null) return "请提供 recordId。";
                var payload = agentReads.listAttachments(ctx, recId);
                return json.writeValueAsString(payload.data().get("data"));
            }
            if ("read_attachment_content".equals(name)) {
                if (referenceId != null) {
                    return chatReferences.formatForContext(actor, projectId, List.of(referenceId));
                }
                log.info("read_attachment_content args: {}", args.toString());
                JsonNode fileNode = args.path("filename");
                String rawId =
                        fileNode.isMissingNode()
                                ? args.path("attachmentId").asText()
                                : fileNode.asText();
                UUID attachmentId;
                try {
                    attachmentId = UUID.fromString(rawId);
                } catch (IllegalArgumentException e) {
                    // LLM passed a filename instead of UUID — search by exact filename
                    if (rawId.isBlank()) {
                        return "未提供文件名。请先调用 list_project_attachments 获取文件列表。";
                    }
                    var listPayload = agentReads.listProjectAttachments(ctx);
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> attachments =
                            (List<Map<String, Object>>) listPayload.data().get("data");
                    Map<String, Object> matched = null;
                    String search = rawId.trim();
                    for (Map<String, Object> row : attachments) {
                        String fname = String.valueOf(row.get("original_filename"));
                        if (fname.equals(search)
                                || fname.endsWith(search)
                                || search.endsWith(fname)) {
                            matched = row;
                            break;
                        }
                    }
                    if (matched == null) {
                        log.warn(
                                "read_attachment_content: no filename match for '{}' among {} attachments",
                                rawId,
                                attachments.size());
                        return "未找到文件 '" + rawId + "'，请确认文件名与列表中的一致。";
                    }
                    attachmentId = UUID.fromString(String.valueOf(matched.get("id")));
                    log.info(
                            "read_attachment_content: resolved filename '{}' to attachmentId={}",
                            rawId,
                            attachmentId);
                }
                UUID recId =
                        args.has("recordId") && !args.path("recordId").isNull()
                                ? UUID.fromString(args.path("recordId").asText())
                                : recordId;
                var payload = agentReads.readAttachmentContent(ctx, attachmentId, recId);
                Map<String, Object> data = (Map<String, Object>) payload.data().get("data");
                String content = String.valueOf(data.get("content"));
                if (content.length() > TOOL_CONTENT_MAX_CHARS) {
                    content = content.substring(0, TOOL_CONTENT_MAX_CHARS) + "\n...(内容过长，已截断)";
                }
                StringBuilder sb = new StringBuilder();
                sb.append("文件: ").append(data.get("filename")).append("\n");
                sb.append("类型: ").append(data.get("mediaType")).append("\n");
                sb.append("内容:\n").append(content);
                return sb.toString();
            }
            if ("plot_chart".equals(name)) {
                String chartType = args.path("chartType").asText("line");
                String xColumn = args.path("xColumn").asText();
                String yColumn = args.path("yColumn").asText();
                String csvHint = args.path("csvFilename").asText(null);
                if (xColumn.isBlank() || yColumn.isBlank()) {
                    return "请提供 xColumn 和 yColumn 参数，例如 xColumn=时间 yColumn=残糖。";
                }
                FitModels.FitProposal proposal =
                        new FitModels.FitProposal(
                                true,
                                "y=a+b*x",
                                false,
                                List.of(),
                                xColumn,
                                yColumn,
                                "CSV_ATTACHMENT",
                                csvHint,
                                List.of(),
                                List.of(),
                                null,
                                null,
                                null,
                                null,
                                false,
                                false);
                List<FitModels.DataPoint> points;
                try {
                    points =
                            referenceId == null
                                    ? curveFits.extractPlotPoints(actor, projectId, proposal)
                                    : referencePoints(
                                            actor,
                                            projectId,
                                            referenceId,
                                            xColumn,
                                            yColumn);
                } catch (ApiException e) {
                    return "未找到可用于绘图的数据。请确认列名 '" + xColumn + "' 和 '" + yColumn + "' 在附件中存在。";
                }
                if (points.size() > 200) points = new ArrayList<>(points.subList(0, 200));
                List<AgentDtos.ChartPointView> chartPoints = new ArrayList<>();
                for (FitModels.DataPoint p : points) {
                    chartPoints.add(new AgentDtos.ChartPointView(p.x(), p.y(), p.recordCode()));
                }
                String title =
                        ("bar".equals(chartType)
                                        ? "柱状图"
                                        : "scatter".equals(chartType) ? "散点图" : "折线图")
                                + "："
                                + yColumn
                                + " vs "
                                + xColumn;
                AgentDtos.ChartView chart =
                        new AgentDtos.ChartView(
                                chartType,
                                title,
                                xColumn,
                                yColumn,
                                List.of(new AgentDtos.ChartSeriesView(yColumn, chartPoints)));
                try {
                    return "CHART:" + json.writeValueAsString(chart);
                } catch (Exception e) {
                    return "图表生成失败: " + e.getMessage();
                }
            }
            if ("fit_data".equals(name)) {
                String equation =
                        FitMethodCatalog.normalizeEquation(args.path("equation").asText("y=a+b*x"));
                String xColumn = args.path("xColumn").asText();
                String yColumn = args.path("yColumn").asText();
                boolean autoCompare = args.path("autoCompare").asBoolean(false);
                if (xColumn.isBlank() || yColumn.isBlank()) {
                    return "STOP. 请提供 xColumn 和 yColumn 参数。不要重试。";
                }
                FitModels.FitProposal proposal =
                        new FitModels.FitProposal(
                                true,
                                equation,
                                autoCompare,
                                List.of(),
                                xColumn,
                                yColumn,
                                "CSV_ATTACHMENT",
                                null,
                                List.of(),
                                List.of(),
                                null,
                                null,
                                null,
                                null,
                                false,
                                false);
                List<FitModels.DataPoint> points =
                        referenceId == null
                                ? curveFits.extractPlotPoints(actor, projectId, proposal)
                                : referencePoints(
                                        actor, projectId, referenceId, xColumn, yColumn);
                if (points.isEmpty()) {
                    return "未找到可用于拟合的数据点。请确认列名正确。";
                }
                try {
                    List<FitModels.FitResult> results;
                    if (autoCompare && referenceId == null) {
                        results = curveFits.fitProposalAll(actor, projectId, proposal);
                    } else {
                        CurveFitEngine engine = new CurveFitEngine();
                        FitModels.FitResult result;
                        if (autoCompare) {
                            List<ExpressionParser.CompiledEquation> equations =
                                    FitMethodCatalog.allEquations().stream()
                                            .map(ExpressionParser::parseEquation)
                                            .toList();
                            result = engine.fitBest(equations, points, List.of());
                        } else {
                            result =
                                    engine.fit(
                                            ExpressionParser.parseEquation(equation),
                                            points,
                                            List.of());
                        }
                        results = List.of(result);
                    }
                    List<AgentDtos.FitView> views = results.stream().map(this::toFitView).toList();
                    return "FIT:" + json.writeValueAsString(views);
                } catch (ApiException e) {
                    return "拟合执行失败: " + e.getMessage();
                }
            }
            return "错误: 未知工具 " + name;
        } catch (ApiException e) {
            return "错误: " + e.getMessage();
        } catch (Exception e) {
            log.warn("Chat tool execution failed: name={} message={}", name, e.getMessage());
            return "执行失败，请稍后重试。";
        }
    }

    private UUID allowedReference(String raw, List<UUID> referenceIds) {
        if (raw == null || raw.isBlank()) return null;
        try {
            UUID value = UUID.fromString(raw);
            if (referenceIds != null && referenceIds.contains(value)) return value;
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "RESOURCE_NOT_FOUND",
                    "Reference file is missing, expired, or not attached to this message");
        } catch (IllegalArgumentException e) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "AGENT_REF_INVALID", "Invalid reference file ID");
        }
    }

    private List<FitModels.DataPoint> referencePoints(
            UUID actor, UUID projectId, UUID referenceId, String xColumn, String yColumn) {
        AgentChatReferenceService.ChatReferenceFile file =
                chatReferences.readReferenceFile(actor, projectId, referenceId);
        List<FitModels.DataPoint> points =
                curveFits.extractFromChatReference(
                        file.bytes(), file.filename(), xColumn, yColumn);
        if (points.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "FIT_NO_POINTS",
                    "附件中未找到可用数值点，请核对 x/y 列名和数据格式");
        }
        return points;
    }

    private RestClient restClientFor(AgentCredentials creds) {
        return restClientFor(creds, Math.max(1000, properties.getTimeoutMs()));
    }

    private RestClient restClientFor(AgentCredentials creds, long timeoutMs) {
        String base =
                blank(creds.baseUrl())
                        ? "http://127.0.0.1/"
                        : creds.baseUrl().replaceAll("/*$", "/");
        var factory =
                new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder()
                                .connectTimeout(Duration.ofMillis(Math.max(1000, timeoutMs)))
                                .build());
        factory.setReadTimeout(Duration.ofMillis(Math.max(1000, timeoutMs)));
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
        text.append("purpose=")
                .append(limit(String.valueOf(record.get("purpose")), 1000))
                .append('\n');
        text.append("fieldValues=")
                .append(limit(String.valueOf(record.get("field_values_json")), 2000))
                .append('\n');
        text.append("contentPlainText=")
                .append(limit(String.valueOf(record.get("content_plain_text")), 2000))
                .append('\n');
        List<Map<String, Object>> attachments =
                contextStore.activeAttachments(UUID.fromString(record.get("id").toString()));
        if (!attachments.isEmpty()) {
            text.append("attachments:\n");
            for (Map<String, Object> att : attachments) {
                text.append("- id=")
                        .append(att.get("id"))
                        .append(" ")
                        .append(att.get("original_filename"))
                        .append(" (")
                        .append(att.get("media_type"))
                        .append(", ")
                        .append(att.get("size_bytes"))
                        .append(" B)\n");
            }
        }
        List<Map<String, Object>> artifacts =
                contextStore.latestRecordArtifact(UUID.fromString(record.get("id").toString()));
        appendLatestArtifact(text, artifacts);
        return text.toString();
    }

    private String buildProjectContext(Map<String, Object> project) {
        String projectId = project.get("id").toString();
        StringBuilder text = new StringBuilder();
        text.append("projectId=").append(projectId).append('\n');
        text.append("name=").append(project.get("name")).append('\n');
        text.append("status=").append(project.get("status")).append('\n');
        text.append("description=")
                .append(limit(String.valueOf(project.get("description")), 500))
                .append('\n');
        text.append("detailedDescription=")
                .append(limit(String.valueOf(project.get("detailed_description")), 1000))
                .append('\n');
        UUID projectUuid = UUID.fromString(projectId);
        long members = contextStore.memberCount(projectUuid);
        text.append("memberCount=").append(members).append('\n');
        List<Map<String, Object>> roles = contextStore.memberRoleCounts(projectUuid);
        text.append("membersByRole=");
        for (Map<String, Object> row : roles)
            text.append(row.get("role")).append('=').append(row.get("cnt")).append(';');
        text.append('\n');
        List<Map<String, Object>> statuses = contextStore.recordStatusCounts(projectUuid);
        text.append("recordsByStatus=");
        for (Map<String, Object> row : statuses)
            text.append(row.get("status")).append('=').append(row.get("cnt")).append(';');
        text.append('\n');
        List<Map<String, Object>> records = contextStore.recentRecords(projectUuid);
        text.append("recentRecords:\n");
        for (Map<String, Object> row : records) {
            text.append("- ")
                    .append(row.get("code"))
                    .append(" | ")
                    .append(limit(String.valueOf(row.get("title")), 120))
                    .append(" | ")
                    .append(row.get("status"))
                    .append(" | R")
                    .append(row.get("current_revision_no"))
                    .append('\n');
        }
        List<Map<String, Object>> attachments = contextStore.projectAttachments(projectUuid);
        if (!attachments.isEmpty()) {
            text.append("projectAttachments:\n");
            for (Map<String, Object> att : attachments) {
                text.append("- id=")
                        .append(att.get("id"))
                        .append(" ")
                        .append(att.get("original_filename"))
                        .append(" (")
                        .append(att.get("media_type"))
                        .append(", ")
                        .append(att.get("size_bytes"))
                        .append(" B) [")
                        .append(att.get("record_code"))
                        .append(" ")
                        .append(att.get("record_title"))
                        .append("]\n");
            }
        }
        List<Map<String, Object>> artifacts = contextStore.latestProjectArtifact(projectUuid);
        appendLatestArtifact(text, artifacts);
        return text.toString();
    }

    private void appendLatestArtifact(StringBuilder text, List<Map<String, Object>> artifacts) {
        if (artifacts.isEmpty()) return;
        try {
            JsonNode content = json.readTree(String.valueOf(artifacts.get(0).get("content_json")));
            text.append("latestSummaryHeadline=")
                    .append(limit(content.path("headline").asText(""), 300))
                    .append('\n');
            text.append("latestExecutiveSummary=")
                    .append(limit(content.path("executiveSummary").asText(""), 800))
                    .append('\n');
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
            if (!"user".equals(role) && !"assistant".equals(role))
                throw invalid("history role must be user or assistant");
            if (content.isEmpty()) continue;
            if (content.length() > 4000) throw invalid("history content exceeds 4000 characters");
            values.add(new AgentDtos.ChatMessage(role, content, null));
        }
        if (values.size() > 20) throw invalid("history exceeds 20 messages");
        return values;
    }

    private String buildFitSummary(List<AgentDtos.FitView> fits) {
        if (fits == null || fits.isEmpty()) return "拟合已完成（无结果）。";
        StringBuilder sb = new StringBuilder("拟合已完成。请引用以下真实数据回复用户，不要编造信息：\n");
        for (int i = 0; i < fits.size(); i++) {
            AgentDtos.FitView f = fits.get(i);
            sb.append("- ")
                    .append(f.equation())
                    .append(": R²=")
                    .append(String.format("%.4f", f.rSquared()))
                    .append(", RMSE=")
                    .append(String.format("%.4f", f.rmse()))
                    .append(", n=")
                    .append(f.n());
            if (f.usedRecordCodes() != null && !f.usedRecordCodes().isEmpty()) {
                sb.append(", 记录=").append(String.join(",", f.usedRecordCodes()));
            }
            if (f.skipped() != null && !f.skipped().isEmpty()) {
                sb.append(", 跳过").append(f.skipped().size()).append("条");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private Map<String, Object> requireVisibleRecord(UUID actor, UUID recordId) {
        return contextStore
                .findVisibleRecord(actor, recordId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND,
                                        "RESOURCE_NOT_FOUND",
                                        "Resource not found or inaccessible"));
    }

    private Map<String, Object> requireVisibleProject(UUID actor, UUID projectId) {
        return contextStore
                .findVisibleProject(actor, projectId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND,
                                        "RESOURCE_NOT_FOUND",
                                        "Resource not found or inaccessible"));
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            log.warn(
                    "Agent chat request rejected: agent.enabled=false (set AGENT_ENABLED=true to enable)");
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AGENT_DISABLED",
                    "Agent functionality is disabled");
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

    @Override
    public List<AgentDtos.ChatSessionSummary> listProjectSessions(UUID actor, UUID projectId) {
        return sessions.listByProject(actor, projectId);
    }

    @Override
    public List<AgentDtos.ChatSessionSummary> listRecordSessions(UUID actor, UUID recordId) {
        return sessions.listByRecord(actor, recordId);
    }

    @Override
    public AgentDtos.ChatSessionDetail getSession(UUID actor, UUID sessionId) {
        return sessions.get(actor, sessionId);
    }

    @Override
    public AgentDtos.ChatSessionDetail saveSession(
            UUID actor, UUID projectId, UUID recordId, AgentDtos.SaveSessionRequest request) {
        return sessions.save(actor, projectId, recordId, request);
    }

    @Override
    public AgentDtos.ChatSessionDetail appendSessionMessages(
            UUID actor, UUID sessionId, List<AgentDtos.ChatMessage> messages) {
        return sessions.appendMessages(actor, sessionId, messages);
    }

    @Override
    public void deleteSession(UUID actor, UUID sessionId) {
        sessions.delete(actor, sessionId);
    }

    private record ParsedChat(String message, List<AgentDtos.ChatMessage> history) {}
}
