package com.bionote.agent.fit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FitIntentParser {
    private static final Pattern EQUATION = Pattern.compile("(?i)(?:方程\\s*[:：]\\s*)?(y\\s*=\\s*[^;；\\n]+)");
    private static final Pattern X_SPEC = Pattern.compile("(?i)(?:xField|自变量|x列)\\s*[=:：]\\s*([A-Za-z0-9_\\-]+)|(?:^|[；;，,\\s])x\\s*[=:：]\\s*([A-Za-z0-9_\\-]+)");
    private static final Pattern Y_SPEC = Pattern.compile("(?i)(?:yField|因变量|y列)\\s*[=:：]\\s*([A-Za-z0-9_\\-]+)");
    private static final Pattern CODE = Pattern.compile("EXP-\\d{8}-[A-Za-z0-9]+");
    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*\"equation\"[\\s\\S]*}");

    private final ObjectMapper json;

    public FitIntentParser(ObjectMapper json) {
        this.json = json;
    }

    public FitModels.FitIntent parse(String message) {
        if (message == null || message.isBlank()) {
            return new FitModels.FitIntent(false, null, null, null, null, null, List.of(), List.of(), null, null, null);
        }
        FitModels.FitIntent fromJson = tryJson(message);
        if (fromJson != null) return fromJson;

        String equation = firstGroup(EQUATION, message);
        if (equation != null) {
            equation = equation.trim();
            if (!equation.toLowerCase(Locale.ROOT).startsWith("y=")) {
                Matcher eq = Pattern.compile("(?i)y\\s*=\\s*[^;；\\n]+").matcher(equation);
                if (eq.find()) equation = eq.group();
            }
        }
        String xSpec = firstGroup(X_SPEC, message);
        String ySpec = firstGroup(Y_SPEC, message);

        // Only treat as a fit request when concrete parameters are provided
        boolean requested = equation != null || (xSpec != null && ySpec != null);
        if (!requested) {
            return new FitModels.FitIntent(false, null, null, null, null, null, List.of(), List.of(), null, null, null);
        }

        List<String> statuses = new ArrayList<>();
        String upper = message.toUpperCase(Locale.ROOT);
        boolean statusScoped = message.contains("状态") || message.contains("仅") || message.contains("筛选")
                || message.contains("只要") || message.contains("限定");
        if (statusScoped) {
            for (String status : List.of("COMPLETED", "IN_PROGRESS", "IN_REVIEW", "CHANGES_REQUESTED")) {
                if (upper.contains(status)) statuses.add(status);
            }
        }

        List<String> codes = new ArrayList<>();
        boolean codeScoped = message.contains("记录") || message.contains("编号") || message.contains("只要")
                || message.contains("仅") || message.contains("筛选") || message.contains("这篇")
                || message.contains("该记录");
        if (codeScoped) {
            Matcher codeMatcher = CODE.matcher(message);
            while (codeMatcher.find()) codes.add(codeMatcher.group());
        }

        String experimentType = null;
        Matcher typeMatcher = Pattern.compile("(?i)(?:实验类型|类型)\\s*[=:：]?\\s*([\\w\\u4e00-\\u9fff]+)").matcher(message);
        if (typeMatcher.find()) experimentType = typeMatcher.group(1);

        String pointSource = "AUTO";
        if (message.toLowerCase(Locale.ROOT).contains("csv") || message.toLowerCase(Locale.ROOT).contains("xlsx")
                || message.toLowerCase(Locale.ROOT).contains("excel") || message.contains("附件")
                || message.contains("表格")) {
            pointSource = "CSV_ATTACHMENT";
        }
        if (message.contains("字段")) pointSource = "TEMPLATE_FIELDS";

        String csvHint = null;
        Matcher csvMatcher = Pattern.compile("(?i)(?:csv|xlsx|excel)\\s*[=:：]?\\s*([\\w.\\-]+)").matcher(message);
        if (csvMatcher.find()) csvHint = csvMatcher.group(1);

        String missing = null;
        if (equation == null && (xSpec == null || ySpec == null)) missing = "请指定拟合方程（如 y = a + b*x）或自变量/因变量列名（如 xField=concentration yField=ct）";
        else if (equation != null && (xSpec == null || ySpec == null)) missing = "请指定自变量与因变量，例如：xField=concentration，yField=ct；或 CSV 列名/列号";

        return new FitModels.FitIntent(true, equation, xSpec, ySpec, pointSource, csvHint, codes, statuses, experimentType, null, missing);
    }

    private FitModels.FitIntent tryJson(String message) {
        Matcher matcher = JSON_BLOCK.matcher(message);
        if (!matcher.find()) return null;
        try {
            JsonNode node = json.readTree(matcher.group());
            String equation = text(node, "equation");
            if (equation == null) return null;
            List<String> codes = new ArrayList<>();
            if (node.path("recordCodes").isArray()) node.path("recordCodes").forEach(v -> codes.add(v.asText()));
            List<String> statuses = new ArrayList<>();
            if (node.path("statuses").isArray()) node.path("statuses").forEach(v -> statuses.add(v.asText()));
            String xSpec = text(node, "xSpec", "xField", "x");
            String ySpec = text(node, "ySpec", "yField", "yColumn");
            String missing = null;
            if (xSpec == null || ySpec == null) missing = "请在 JSON 中提供 xSpec 与 ySpec";
            return new FitModels.FitIntent(true, equation, xSpec, ySpec, text(node, "pointSource"), text(node, "csvNameHint"),
                    codes, statuses, text(node, "experimentType"), text(node, "keyword"), missing);
        } catch (Exception e) {
            return null;
        }
    }

    private String firstGroup(Pattern pattern, String message) {
        Matcher matcher = pattern.matcher(message);
        if (!matcher.find()) return null;
        for (int i = 1; i <= matcher.groupCount(); i++) {
            if (matcher.group(i) != null && !matcher.group(i).isBlank()) return matcher.group(i).trim();
        }
        return matcher.group().trim();
    }

    private String first(Pattern pattern, String message) {
        Matcher matcher = pattern.matcher(message);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String text(JsonNode node, String... keys) {
        for (String key : keys) {
            if (node.hasNonNull(key) && !node.get(key).asText().isBlank()) return node.get(key).asText().trim();
        }
        return null;
    }
}
