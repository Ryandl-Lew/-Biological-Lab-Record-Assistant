package com.bionote.agent.fit;

import com.bionote.agent.config.AgentCredentialService;
import com.bionote.agent.config.AgentCredentials;
import com.bionote.agent.config.AgentProperties;
import com.bionote.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FitProposalResolver {
    private static final Pattern CODE = Pattern.compile("EXP-\\d{8}-[A-Za-z0-9]+");

    private static final String PROPOSAL_SYSTEM = """
            You are BioNote's curve-fit proposal planner. Return ONLY one JSON object (no markdown) with keys:
            fitRequested (boolean), equation (string|null), autoCompare (boolean), candidateIds (string[]),
            xSpec (string|null), ySpec (string|null), pointSource (AUTO|TEMPLATE_FIELDS|CSV_ATTACHMENT),
            csvNameHint (string|null), recordCodes (string[]), statuses (string[]), experimentType (string|null),
            keyword (string|null), rationale (string), missingPrompt (string|null),
            multivariate (boolean), timeToMinutes (boolean).
            Rules:
            - Prefer fields and table column names that appear in FIT_DATA_CATALOG (see file="..." columns=[...]).
            - When columns=[...] is present, you CAN see Excel/CSV headers; never claim columns are unknown or that template fields are required.
            - For multiple predictors (多元/自变量列表/其余列除备注), set multivariate=true, autoCompare=false, equation=null;
              put ALL predictor column names in xSpec as a comma-separated list; put ALL response columns in ySpec (comma-separated).
            - Never put ellipsis equations like y=a+b1*x1+... into equation; use multivariate=true instead.
            - If the user asks to convert clock time to minutes with earliest=0, set timeToMinutes=true and keep the time column in xSpec.
            - When multivariate=true, NEVER ask the user to pick univariate catalog models (linear/quadratic/log/exp);
              ignore those method names and keep equation=null.
            - If the user clearly chooses a univariate method (linear/quadratic/exp decay/growth/log) AND only one x column,
              set autoCompare=false and equation from catalog.
            - If the user is unsure or asks to try/auto-select models (and not multivariate), set autoCompare=true, equation=null, candidateIds from catalog ids.
            - If x/y mapping cannot be determined, set missingPrompt in Chinese listing available columns from the catalog and leave specs null.
            - Do not invent record codes or filenames that are absent from the catalog unless the user typed them explicitly.
            """;

    private final AgentProperties properties;
    private final ObjectMapper json;
    private final FitIntentParser intents;
    private final AgentCredentialService credentials;

    public FitProposalResolver(AgentProperties properties, ObjectMapper json, FitIntentParser intents) {
        this(properties, json, intents, null);
    }

    @Autowired
    public FitProposalResolver(AgentProperties properties, ObjectMapper json, FitIntentParser intents,
                               AgentCredentialService credentials) {
        this.properties = properties;
        this.json = json;
        this.intents = intents;
        this.credentials = credentials;
    }

    public FitModels.FitProposal resolve(String message, String catalogText, FitModels.FitIntent seed) {
        return resolve(message, catalogText, seed, null);
    }

    public FitModels.FitProposal resolve(String message, String catalogText, FitModels.FitIntent seed,
                                         AgentCredentials creds) {
        AgentCredentials effective = creds != null ? creds : systemFallback();
        String provider = blank(effective.provider()) ? "fake" : effective.provider();
        if ("fake".equalsIgnoreCase(provider)) {
            return resolveFake(message, catalogText, seed);
        }
        try {
            FitModels.FitProposal fromLlm = resolveWithLlm(message, catalogText, effective);
            if (fromLlm != null) return enrichFromMessage(mergeSeed(fromLlm, seed), message, catalogText);
        } catch (ApiException e) {
            // fall back to heuristics
        }
        return resolveFake(message, catalogText, seed);
    }

    private AgentCredentials systemFallback() {
        if (credentials != null) return credentials.resolve(null);
        return new AgentCredentials(
                blank(properties.getProvider()) ? "fake" : properties.getProvider(),
                blank(properties.getModel()) ? "fake-deterministic-v1" : properties.getModel(),
                blank(properties.getBaseUrl()) ? "" : properties.getBaseUrl().replaceAll("/*$", "/"),
                properties.getApiKey() == null ? "" : properties.getApiKey()
        );
    }

    /** Prefer explicit multi-column / time / multivariate cues from the user message over LLM omissions. */
    private FitModels.FitProposal enrichFromMessage(FitModels.FitProposal base, String message, String catalogText) {
        if (base == null) return null;
        String[] multi = parseMultiColumnIntent(message, catalogText);
        String xSpec = multi[0] != null ? multi[0] : base.xSpec();
        String ySpec = multi[1] != null ? multi[1] : base.ySpec();
        boolean timeToMinutes = base.timeToMinutes() || message.contains("分钟") || message.contains("最早")
                || (xSpec != null && xSpec.contains("时间"));
        boolean multivariate = base.multivariate()
                || message.contains("多元")
                || (message.contains("自变量") && (message.contains("列表") || message.contains("多个")
                || message.contains("其余") || message.contains("其他列") || message.contains("除备注")))
                || CurveFitService.splitSpecs(xSpec).size() > 1
                || CurveFitService.looksLikeMultivariateEquation(base.equation());
        String equation = base.equation();
        boolean auto = base.autoCompare();
        if (multivariate) {
            auto = false;
            equation = null;
        }
        String missing = base.missingPrompt();
        if (xSpec != null && ySpec != null) missing = null;
        String rationale = multivariate
                ? "将按多元线性回归执行：各自变量列联合预测每个因变量"
                        + (timeToMinutes ? "；时间列换算为相对分钟（最早为 0）" : "") + "。"
                : base.rationale();

        // Attachment fits should not inherit LLM-invented record/status filters unless the user scoped them.
        List<String> codes = base.recordCodes() == null ? List.of() : base.recordCodes();
        List<String> statuses = base.statuses() == null ? List.of() : base.statuses();
        if (!userScopedRecords(message)) {
            codes = List.of();
            statuses = List.of();
        }
        return new FitModels.FitProposal(
                base.fitRequested(), equation, auto, base.candidateIds(), xSpec, ySpec,
                base.pointSource(), base.csvNameHint(), codes, statuses,
                userScopedRecords(message) ? base.experimentType() : null,
                base.keyword(), rationale, missing, multivariate, timeToMinutes);
    }

    private FitModels.FitProposal resolveFake(String message, String catalogText, FitModels.FitIntent seed) {
        FitModels.FitIntent base = seed != null ? seed : intents.parse(message);
        if (!base.fitRequested()) {
            return new FitModels.FitProposal(false, null, false, List.of(), null, null, null, null,
                    List.of(), List.of(), null, null, null, null, false, false);
        }
        boolean auto = FitMethodCatalog.wantsAutoCompare(message);
        String equation = base.equation();
        if (!auto) {
            String mapped = FitMethodCatalog.resolveFromMessage(message);
            if (mapped != null) equation = mapped;
            if (equation == null && base.equation() != null) equation = base.equation();
        } else {
            equation = null;
        }

        String xSpec = base.xSpec();
        String ySpec = base.ySpec();
        String pointSource = base.pointSource() == null ? "AUTO" : base.pointSource();
        String csvHint = base.csvNameHint();
        List<String> codes = new ArrayList<>(base.recordCodes() == null ? List.of() : base.recordCodes());
        List<String> statuses = new ArrayList<>(base.statuses() == null ? List.of() : base.statuses());

        if (!userScopedRecords(message)) {
            codes = new ArrayList<>();
            statuses = new ArrayList<>();
        } else if (codes.isEmpty()) {
            Matcher codeMatcher = CODE.matcher(latestUserTurn(message));
            while (codeMatcher.find()) codes.add(codeMatcher.group());
        }
        if ((message.toLowerCase(Locale.ROOT).contains("csv")
                || message.toLowerCase(Locale.ROOT).contains("xlsx")
                || message.toLowerCase(Locale.ROOT).contains("excel")
                || message.contains("附件") || message.contains("标曲") || message.contains("表格"))
                && !"TEMPLATE_FIELDS".equals(pointSource)) {
            pointSource = "CSV_ATTACHMENT";
        }
        if (csvHint == null && catalogText != null) {
            csvHint = pickTableFromCatalog(message, catalogText);
        }
        if (xSpec == null || ySpec == null) {
            String[] guessed = guessXy(message, catalogText, pointSource);
            if (xSpec == null) xSpec = guessed[0];
            if (ySpec == null) ySpec = guessed[1];
        }

        // Prefer structured multi-column intents from natural language.
        String[] multi = parseMultiColumnIntent(message, catalogText);
        if (multi[0] != null) xSpec = multi[0];
        if (multi[1] != null) ySpec = multi[1];

        boolean timeToMinutes = message.contains("分钟") || message.contains("最早")
                || (xSpec != null && xSpec.contains("时间"));
        boolean multivariate = message.contains("多元")
                || (message.contains("自变量") && (message.contains("列表") || message.contains("多个")
                || message.contains("其余") || message.contains("其他列") || message.contains("除备注")))
                || CurveFitService.splitSpecs(xSpec).size() > 1
                || CurveFitService.looksLikeMultivariateEquation(equation);

        if ("CSV_ATTACHMENT".equals(pointSource) && csvHint == null && catalogText != null) {
            Matcher named = Pattern.compile("(?i)([^\\s|;\\[\\]]+\\.(?:csv|xlsx))").matcher(message);
            if (named.find()) csvHint = named.group(1);
        }

        List<String> availableColumns = extractColumnsFromCatalog(catalogText);
        String missing = null;
        if (xSpec == null || ySpec == null) {
            if (!availableColumns.isEmpty()) {
                missing = "该表格可用列：" + String.join("、", availableColumns)
                        + "。请指定哪一列作为 x（自变量）、哪一列作为 y（因变量）。多因变量可用顿号分隔，例如：残糖、OD。";
            } else {
                missing = "请补充自变量与因变量（字段名或表格列名），例如：x 用时间，y 用含糖量。";
            }
        } else if (!auto && !multivariate && (equation == null || equation.isBlank())) {
            String custom = FitMethodCatalog.extractCustomEquation(message);
            if (custom != null) {
                equation = custom;
            } else if ("CSV_ATTACHMENT".equals(pointSource) || message.contains("附件") || message.contains("表格")) {
                auto = true;
            } else {
                missing = "请指定拟合方法（如线性/幂函数/米氏），或直接写自定义方程如 y=a*x/(b+x)，或说明方程不确定以便多模型比选。";
            }
        }
        if (multivariate) {
            auto = false;
            equation = null;
        }

        String rationale;
        if (multivariate) {
            rationale = "将按多元线性回归执行：各自变量列联合预测每个因变量；时间列会换算为相对分钟（最早为 0）。"
                    + "多自变量场景不使用单变量对数/指数等目录方程。";
        } else if (auto) {
            rationale = "你表示方程不确定或未指定方法，将在确认后对预置候选模型比选最优结果。";
        } else if (equation != null) {
            final String eqText = equation.replace(" ", "");
            boolean preset = FitMethodCatalog.allEquations().stream()
                    .anyMatch(eq -> eq.equalsIgnoreCase(eqText));
            rationale = preset
                    ? "按你指定的方法映射到方程 " + equation + "。"
                    : "按你提供的自定义方程 " + equation + " 做单变量拟合（支持 a–z 参数与 exp/log/sqrt 等）。";
        } else {
            rationale = "已根据项目数据目录拟定拟合方案，请确认后执行。";
        }

        List<String> candidates = auto
                ? FitMethodCatalog.all().stream().map(FitMethodCatalog.Method::id).toList()
                : List.of();

        return new FitModels.FitProposal(true, auto ? null : equation, auto, candidates, xSpec, ySpec, pointSource, csvHint,
                codes, statuses, base.experimentType(), base.keyword(), rationale, missing,
                multivariate, timeToMinutes);
    }

    private FitModels.FitProposal resolveWithLlm(String message, String catalogText, AgentCredentials creds) {
        String model = creds.model();
        if (blank(creds.baseUrl()) || blank(creds.apiKey()) || blank(model)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MODEL_PROVIDER_UNAVAILABLE", "Agent provider is not configured");
        }
        String user = "USER_MESSAGE:\n" + message + "\n\nFIT_DATA_CATALOG:\n" + catalogText
                + "\n\nEQUATION_CATALOG:\n" + FitMethodCatalog.catalogDescription();
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", PROPOSAL_SYSTEM),
                Map.of("role", "user", "content", user)
        );
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", 0);
        body.put("max_tokens", 800);
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
                throw new ApiException(HttpStatus.BAD_GATEWAY, "MODEL_PROVIDER_ERROR", "Empty proposal content");
            }
            return parseProposalJson(content);
        } catch (ApiException e) {
            throw e;
        } catch (RestClientResponseException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "MODEL_PROVIDER_ERROR", "Proposal model call failed");
        } catch (ResourceAccessException e) {
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "AGENT_TIMEOUT", "Proposal model timed out");
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "MODEL_PROVIDER_ERROR", "Proposal parse failed");
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

    FitModels.FitProposal parseProposalJson(String content) throws Exception {
        String value = content.trim();
        if (value.startsWith("```")) {
            int newline = value.indexOf('\n');
            int fence = value.lastIndexOf("```");
            if (newline > 2 && fence > newline) value = value.substring(newline + 1, fence).trim();
        }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start >= 0 && end > start) value = value.substring(start, end + 1);
        JsonNode node = json.readTree(value);
        boolean auto = node.path("autoCompare").asBoolean(false);
        List<String> candidates = readStringList(node.path("candidateIds"));
        if (auto && candidates.isEmpty()) {
            candidates = FitMethodCatalog.all().stream().map(FitMethodCatalog.Method::id).toList();
        }
        String equation = text(node, "equation");
        if (!auto && equation == null) {
            String mapped = FitMethodCatalog.resolveFromMessage(text(node, "rationale") == null ? "" : text(node, "rationale"));
            equation = mapped;
        }
        String missing = text(node, "missingPrompt");
        String xSpec = text(node, "xSpec", "xField");
        String ySpec = text(node, "ySpec", "yField");
        if ((xSpec == null || ySpec == null) && missing == null) {
            missing = "请补充自变量与因变量映射。";
        }
        boolean multivariate = node.path("multivariate").asBoolean(false)
                || CurveFitService.splitSpecs(xSpec).size() > 1
                || CurveFitService.looksLikeMultivariateEquation(equation);
        boolean timeToMinutes = node.path("timeToMinutes").asBoolean(false)
                || (xSpec != null && xSpec.contains("时间"));
        if (multivariate) {
            auto = false;
            equation = null;
        }
        List<String> codes = readStringList(node.path("recordCodes"));
        List<String> statuses = readStringList(node.path("statuses"));
        // Drop model-invented record filters; enrichFromMessage will re-apply if the user scoped them.
        if (codes == null) codes = List.of();
        if (statuses == null) statuses = List.of();
        return new FitModels.FitProposal(
                node.path("fitRequested").asBoolean(true),
                equation,
                auto,
                candidates,
                xSpec,
                ySpec,
                text(node, "pointSource") == null ? "AUTO" : text(node, "pointSource"),
                text(node, "csvNameHint"),
                codes,
                statuses,
                text(node, "experimentType"),
                text(node, "keyword"),
                text(node, "rationale") == null ? "已拟定拟合方案，请确认后执行。" : text(node, "rationale"),
                missing,
                multivariate,
                timeToMinutes
        );
    }

    private FitModels.FitProposal mergeSeed(FitModels.FitProposal llm, FitModels.FitIntent seed) {
        if (seed == null) return llm;
        return new FitModels.FitProposal(
                true,
                llm.equation() != null ? llm.equation() : seed.equation(),
                llm.autoCompare(),
                llm.candidateIds(),
                llm.xSpec() != null ? llm.xSpec() : seed.xSpec(),
                llm.ySpec() != null ? llm.ySpec() : seed.ySpec(),
                llm.pointSource() != null ? llm.pointSource() : seed.pointSource(),
                llm.csvNameHint() != null ? llm.csvNameHint() : seed.csvNameHint(),
                llm.recordCodes() != null && !llm.recordCodes().isEmpty() ? llm.recordCodes() : seed.recordCodes(),
                llm.statuses() != null && !llm.statuses().isEmpty() ? llm.statuses() : seed.statuses(),
                llm.experimentType() != null ? llm.experimentType() : seed.experimentType(),
                llm.keyword() != null ? llm.keyword() : seed.keyword(),
                llm.rationale(),
                llm.missingPrompt(),
                llm.multivariate(),
                llm.timeToMinutes()
        );
    }

    /** Returns [xSpec, ySpec] when user lists 自变量/因变量 in Chinese. Uses the latest mention. */
    private String[] parseMultiColumnIntent(String message, String catalogText) {
        if (message == null || message.isBlank()) return new String[]{null, null};
        String x = null, y = null;

        Matcher axisY = Pattern.compile(
                "(?i)(?:y轴|纵轴)\\s*[=：:为是]\\s*([^\\n；;，,]+)|(?:y轴|纵轴)\\s+([\\w\\u4e00-\\u9fff（）()-]{1,40})"
        ).matcher(message);
        while (axisY.find()) {
            String raw = axisY.group(1) != null ? axisY.group(1) : axisY.group(2);
            String parsed = normalizeAxisColumn(raw, catalogText);
            if (parsed != null && !isProbablyEquation(parsed)) y = parsed;
        }
        Matcher axisX = Pattern.compile(
                "(?i)(?:x轴|横轴)\\s*[=：:为是]\\s*([^\\n；;，,]+)|(?:x轴|横轴)\\s+([\\w\\u4e00-\\u9fff（）()-]{1,40})"
        ).matcher(message);
        while (axisX.find()) {
            String raw = axisX.group(1) != null ? axisX.group(1) : axisX.group(2);
            String parsed = normalizeAxisColumn(raw, catalogText);
            if (parsed != null) x = parsed;
        }

        Matcher yMatcher = Pattern.compile(
                "(?i)(?:因变量\\s*[:：为是]?|\\by\\s*[=：:是为])\\s*([^\\n；;]+)"
        ).matcher(message);
        while (yMatcher.find()) {
            String raw = truncateBeforeXClause(yMatcher.group(1));
            String parsed = normalizeColumnList(raw, catalogText);
            // Skip equation fragments accidentally captured from y=... patterns
            if (parsed != null && !isProbablyEquation(parsed)) y = parsed;
        }

        Matcher xMatcher = Pattern.compile(
                "(?i)(?:自变量\\s*[:：为是]?|\\bx\\s*(?:包括|[=：:为]))\\s*([^\\n；;]+)"
        ).matcher(message);
        while (xMatcher.find()) {
            String parsed = normalizeColumnList(xMatcher.group(1), catalogText);
            if (parsed != null) x = parsed;
        }

        // Legacy shorthand: only if no newer explicit y was chosen.
        if (y == null) {
            String lower = message.toLowerCase(Locale.ROOT);
            if (message.contains("残糖") && (message.contains("OD") || lower.contains("od"))
                    && !message.contains("耗碱")) {
                y = "残糖,OD";
            }
        }

        if (x == null) {
            String latest = latestUserTurn(message);
            if ((latest.contains("其余") || latest.contains("其他") || latest.contains("除了") || latest.contains("除备注"))
                    && latest.contains("备注") && catalogText != null) {
                List<String> columns = extractColumnsFromCatalog(catalogText);
                List<String> ys = CurveFitService.splitSpecs(y);
                List<String> xs = new ArrayList<>();
                for (String col : columns) {
                    if ("备注".equals(col)) continue;
                    if (ys.stream().anyMatch(v -> columnMatchesToken(col, v))) continue;
                    xs.add(col);
                }
                if (!xs.isEmpty()) x = String.join(",", xs);
            }
        }
        return new String[]{x, y};
    }

    private String truncateBeforeXClause(String raw) {
        if (raw == null) return null;
        Matcher cut = Pattern.compile("(?i)[,，\\s]*(?:x\\s*(?:包括|[=：:为])|自变量)").matcher(raw);
        if (cut.find()) return raw.substring(0, cut.start()).trim();
        return raw.trim();
    }

    private String normalizeColumnList(String raw, String catalogText) {
        List<String> available = extractColumnsFromCatalog(catalogText);
        List<String> picked = new ArrayList<>();
        for (String part : raw.split("[,，、/|与和以及]+")) {
            String token = part.replaceAll("(?i)(分别拟合|等|列|作为|自变量|因变量|包括)", "").trim();
            if (token.isEmpty()) continue;
            if (token.contains("备注") && !token.contains("耗碱") && token.length() <= 6) continue;
            String matched = null;
            for (String col : available) {
                if (columnMatchesToken(col, token)) {
                    matched = col;
                    break;
                }
            }
            if (matched != null && !picked.contains(matched)) picked.add(matched);
            else if (!token.isEmpty() && token.length() < 40 && !picked.contains(token)) picked.add(token);
        }
        return picked.isEmpty() ? null : String.join(",", picked);
    }

    /** Axis phrases must resolve to a real catalog column; never keep instructional fragments. */
    private String normalizeAxisColumn(String raw, String catalogText) {
        if (raw == null) return null;
        String token = raw.replaceAll("(?i)(分别拟合|等|列|作为|包括|和一|与一)", "").trim();
        if (token.isEmpty() || "。".equals(token) || token.length() > 40) return null;
        if (token.contains("横轴") || token.contains("纵轴") || token.contains("选择")) return null;
        List<String> available = extractColumnsFromCatalog(catalogText);
        for (String col : available) {
            if (columnMatchesToken(col, token) || token.equals(col)) return col;
        }
        // Allow bare names only when catalog is empty (tests / fields-only projects).
        if (available.isEmpty() && token.matches("[\\w\\u4e00-\\u9fff（）()-]+")) return token;
        return null;
    }

    /** Returns true when the value looks like a math equation rather than a column name. */
    private boolean isProbablyEquation(String value) {
        if (value == null || value.isBlank()) return false;
        // If it contains math operators and typical equation characters, it's likely an equation
        return value.matches(".*[\\+\\-\\*\\/\\^].*") && !value.matches("[A-Za-z0-9_\\-]+");
    }

    private boolean columnMatchesToken(String col, String token) {
        if (col == null || token == null) return false;
        String c = col.trim();
        String t = token.trim();
        if (c.equalsIgnoreCase(t) || c.contains(t) || t.contains(c)) return true;
        // 耗碱值 ↔ 耗碱量（显示值）
        if (c.contains("耗碱") && t.contains("耗碱")) return true;
        if (c.contains("残糖") && t.contains("残糖")) return true;
        return false;
    }

    /** True only when the latest user turn intentionally scopes records. */
    private boolean userScopedRecords(String message) {
        String latest = latestUserTurn(message);
        if (CODE.matcher(latest).find()) return true;
        String upper = latest.toUpperCase(Locale.ROOT);
        boolean mentionsStatusWord = latest.contains("状态") || latest.contains("仅") || latest.contains("筛选")
                || latest.contains("只要") || latest.contains("限定");
        if (!mentionsStatusWord) return false;
        return upper.contains("COMPLETED") || upper.contains("IN_PROGRESS") || upper.contains("IN_REVIEW")
                || upper.contains("CHANGES_REQUESTED") || latest.contains("已完成") || latest.contains("进行中");
    }

    private String latestUserTurn(String message) {
        if (message == null || message.isBlank()) return "";
        String[] parts = message.split("\\R");
        for (int i = parts.length - 1; i >= 0; i--) {
            String line = parts[i].trim();
            if (!line.isEmpty()) return line;
        }
        return message.trim();
    }

    private String[] guessXy(String message, String catalogText, String pointSource) {
        String lower = message.toLowerCase(Locale.ROOT);
        String x = null, y = null;
        boolean mentionsCsv = "CSV_ATTACHMENT".equals(pointSource) || lower.contains("csv") || message.contains("标曲")
                || message.contains("标准曲线") || lower.contains("qpcr") || message.contains("附件")
                || lower.contains("excel") || lower.contains("xlsx") || message.contains("表格");
        if (lower.contains("log10_copies")) x = "log10_copies";
        else if (lower.contains("concentration") || message.contains("浓度")) x = "concentration";
        else if (lower.contains("dose") || message.contains("剂量")) x = "dose_uM";
        else if (lower.contains("protein") || message.contains("蛋白")) x = "protein_ug_ml";
        else if (message.contains("时间")) x = matchColumn(catalogText, "时间");
        else if (mentionsCsv && catalogText != null && catalogText.contains("log10_copies")) x = "log10_copies";

        if (lower.contains("viability") || message.contains("存活")) y = "viability_pct";
        else if (lower.contains("abs595") || message.contains("吸光")) y = "abs595";
        else if (message.contains("含糖") || message.contains("糖量")) y = matchColumn(catalogText, "含糖");
        else if (lower.contains("ct") || message.contains("Ct") || message.contains("CT")) y = "ct";
        else if (mentionsCsv && catalogText != null && (catalogText.contains(" ct") || catalogText.contains("|ct")
                || catalogText.contains("ct,"))) {
            y = "ct";
        }

        List<String> columns = extractColumnsFromCatalog(catalogText);
        if ((x == null || y == null) && columns.size() == 2 && (mentionsCsv || message.contains("唯一"))) {
            if (x == null) x = columns.get(0);
            if (y == null) y = columns.get(1);
        }
        if (x == null) x = matchColumnMention(message, columns);
        if (y == null) {
            for (String col : columns) {
                if (!col.equals(x) && message.contains(col)) {
                    y = col;
                    break;
                }
            }
        }
        return new String[]{x, y};
    }

    private String matchColumn(String catalogText, String tip) {
        for (String col : extractColumnsFromCatalog(catalogText)) {
            if (col.contains(tip)) return col;
        }
        return tip;
    }

    private String matchColumnMention(String message, List<String> columns) {
        for (String col : columns) {
            if (message.contains(col)) return col;
        }
        return null;
    }

    private List<String> extractColumnsFromCatalog(String catalogText) {
        List<String> columns = new ArrayList<>();
        if (catalogText == null || catalogText.isBlank()) return columns;
        Matcher matcher = Pattern.compile("columns=\\[([^\\]]*)]").matcher(catalogText);
        while (matcher.find()) {
            String body = matcher.group(1).trim();
            if (body.isEmpty()) continue;
            for (String part : body.split(",")) {
                String col = part.trim();
                if (!col.isEmpty() && !columns.contains(col)) columns.add(col);
            }
        }
        return columns;
    }

    private String pickTableFromCatalog(String message, String catalogText) {
        String lower = message.toLowerCase(Locale.ROOT);
        Matcher matcher = Pattern.compile("file=\"([^\"]+)\"").matcher(catalogText);
        String first = null;
        List<String> all = new ArrayList<>();
        while (matcher.find()) {
            String name = matcher.group(1);
            all.add(name);
            if (first == null) first = name;
            String stem = name.toLowerCase(Locale.ROOT).replace(".csv", "").replace(".xlsx", "");
            if (lower.contains(stem) || message.contains(name) || lower.contains(name.toLowerCase(Locale.ROOT))) {
                return name;
            }
        }
        if ((message.contains("唯一") || message.contains("附件") || lower.contains("excel") || lower.contains("xlsx"))
                && all.size() == 1) {
            return all.get(0);
        }
        if (lower.contains("qpcr") || lower.contains("标曲") || lower.contains("标准曲线") || lower.contains("excel")
                || message.contains("附件")) {
            for (String name : all) {
                String n = name.toLowerCase(Locale.ROOT);
                if (n.contains("standard") || n.contains("qpcr") || n.contains("curve") || n.contains("标曲")) {
                    return name;
                }
            }
            if (all.size() == 1) return all.get(0);
        }
        return first;
    }

    private List<String> readStringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                if (item != null && !item.asText().isBlank()) values.add(item.asText().trim());
            }
        }
        return values;
    }

    private String text(JsonNode node, String... keys) {
        for (String key : keys) {
            if (node.hasNonNull(key) && !node.get(key).asText().isBlank() && !"null".equalsIgnoreCase(node.get(key).asText())) {
                return node.get(key).asText().trim();
            }
        }
        return null;
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

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
