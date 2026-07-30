package com.bionote.agent.fit;

import com.bionote.agent.api.AgentDtos;
import com.bionote.agent.config.AgentCredentials;
import com.bionote.common.ApiException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Builds structured chart payloads from project tabular/field data for Agent chat. Axis mapping
 * only accepts catalog column names after x轴/y轴 — never instructional prose.
 */
@Service
public class ChartPlotService {
    private static final int MAX_POINTS = 200;
    private static final Pattern CATALOG_COLUMNS = Pattern.compile("columns=\\[([^\\]]*)]");
    private static final Pattern CATALOG_FILE = Pattern.compile("file=\"([^\"]+\\.(?:csv|xlsx))\"");

    private final CurveFitService curveFits;

    public ChartPlotService(CurveFitService curveFits) {
        this.curveFits = curveFits;
    }

    public boolean looksLikeChartRequest(String message) {
        if (message == null || message.isBlank()) return false;
        String text = message.toLowerCase(Locale.ROOT);
        return message.contains("柱状图")
                || message.contains("折线图")
                || message.contains("画图")
                || message.contains("绘图")
                || message.contains("可视化")
                || message.contains("数据图")
                || text.contains("bar chart")
                || text.contains("line chart")
                || text.contains("plot ")
                || text.endsWith("plot")
                || text.contains("scatter")
                || (text.contains("chart") && !text.contains("flowchart"));
    }

    public boolean looksLikeAxisMapping(String message) {
        if (message == null || message.isBlank()) return false;
        return message.contains("x轴")
                || message.contains("y轴")
                || message.contains("横轴")
                || message.contains("纵轴")
                || message.matches("(?is).*\\bx\\s*[=：:].*\\by\\s*[=：:].*")
                || message.matches("(?is).*\\by\\s*[=：:].*\\bx\\s*[=：:].*");
    }

    public boolean shouldHandleChart(String message, List<AgentDtos.ChatMessage> history) {
        if (looksLikeChartRequest(message)) return true;
        if (!looksLikeAxisMapping(message)) return false;
        if (history == null) return false;
        for (AgentDtos.ChatMessage item : history) {
            if (item == null || item.content() == null) continue;
            String content = item.content();
            if ("user".equalsIgnoreCase(item.role()) && looksLikeChartRequest(content)) return true;
            if ("assistant".equalsIgnoreCase(item.role())
                    && (content.contains("可用列")
                            || content.contains("请指定")
                            || content.contains("请从以下")
                            || content.contains("横轴")
                            || content.contains("纵轴")
                            || content.contains("X轴")
                            || content.contains("Y轴"))) {
                return true;
            }
        }
        return false;
    }

    public String detectChartType(String message) {
        if (message == null) return "line";
        String text = message.toLowerCase(Locale.ROOT);
        if (message.contains("柱状") || text.contains("bar")) return "bar";
        if (message.contains("散点") || text.contains("scatter")) return "scatter";
        return "line";
    }

    public AgentDtos.ChartView plot(
            UUID actor,
            UUID projectId,
            String message,
            String catalogText,
            AgentCredentials creds) {
        String type = detectChartType(message);
        List<String> columns = extractColumns(catalogText);
        String[] explicit = parseExplicitAxes(message, columns);
        String xCol = explicit[0];
        String yCol = explicit[1];

        if (xCol == null || yCol == null) {
            String hint =
                    columns.isEmpty()
                            ? "请说明横轴与纵轴列名，例如：x轴时间，y轴残糖"
                            : "请从以下列中选择横轴（X轴）和纵轴（Y轴）："
                                    + String.join(", ", columns)
                                    + "。回复示例：x轴时间，y轴残糖";
            throw new ApiException(HttpStatus.BAD_REQUEST, "CHART_MISSING_MAPPING", hint);
        }

        String csvHint = pickFileHint(message, catalogText);
        FitModels.FitProposal univariate =
                new FitModels.FitProposal(
                        true,
                        "y=a+b*x",
                        false,
                        List.of(),
                        xCol,
                        yCol,
                        "CSV_ATTACHMENT",
                        csvHint,
                        List.of(),
                        List.of(),
                        null,
                        null,
                        null,
                        null,
                        false,
                        xCol.contains("时间") || xCol.toLowerCase(Locale.ROOT).contains("time"));

        List<FitModels.DataPoint> points =
                curveFits.extractPlotPoints(actor, projectId, univariate);
        if (points.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "CHART_NO_POINTS",
                    "未找到可用于绘图的数值点。请确认 Excel 中存在列「" + xCol + "」与「" + yCol + "」，且为数值/可解析时间。");
        }
        if (points.size() > MAX_POINTS) {
            points = new ArrayList<>(points.subList(0, MAX_POINTS));
        }

        List<AgentDtos.ChartPointView> chartPoints = new ArrayList<>();
        for (FitModels.DataPoint point : points) {
            chartPoints.add(
                    new AgentDtos.ChartPointView(point.x(), point.y(), formatNum(point.x())));
        }

        String title =
                ("bar".equals(type) ? "柱状图" : "scatter".equals(type) ? "散点图" : "折线图")
                        + "："
                        + yCol
                        + " vs "
                        + xCol;
        return new AgentDtos.ChartView(
                type, title, xCol, yCol, List.of(new AgentDtos.ChartSeriesView(yCol, chartPoints)));
    }

    /**
     * Only accept axis values that match catalog columns after x轴/y轴/横轴/纵轴. This avoids matching
     * instructional text such as「一列横轴和一列纵轴」.
     */
    String[] parseExplicitAxes(String message, String catalogText) {
        return parseExplicitAxes(message, extractColumns(catalogText));
    }

    String[] parseExplicitAxes(String message, List<String> columns) {
        if (message == null || message.isBlank() || columns == null || columns.isEmpty()) {
            return new String[] {null, null};
        }
        String x = findAxisColumn(message, columns, true);
        String y = findAxisColumn(message, columns, false);
        return new String[] {x, y};
    }

    private String findAxisColumn(String message, List<String> columns, boolean xAxis) {
        // Longer column names first so「发酵时长」wins over「时间」when both could match.
        List<String> ordered = new ArrayList<>(columns);
        ordered.sort(Comparator.comparingInt(String::length).reversed());
        String found = null;
        Pattern grab =
                xAxis
                        ? Pattern.compile(
                                "(?i)(?:x轴|横轴)\\s*[=：:为是]?\\s*([^,，；;\\n]+?)(?=$|[,，；;]|\\s*y轴|\\s*纵轴)")
                        : Pattern.compile(
                                "(?i)(?:y轴|纵轴)\\s*[=：:为是]?\\s*([^,，；;\\n]+?)(?=$|[,，；;]|\\s*x轴|\\s*横轴)");
        Pattern altGrab =
                xAxis
                        ? Pattern.compile(
                                "(?i)(?:^|[,，\\s])x\\s*[=：:]\\s*([^,，；;\\n]+?)(?=$|[,，；;]|\\s*y\\b)")
                        : Pattern.compile(
                                "(?i)(?:^|[,，\\s])y\\s*[=：:]\\s*([^,，；;\\n]+?)(?=$|[,，；;]|\\s*x\\b)");
        for (String line : message.split("\\R")) {
            String text = line.trim();
            if (text.isEmpty()) continue;
            Matcher matcher = grab.matcher(text);
            while (matcher.find()) {
                String matched = matchColumnToken(matcher.group(1), ordered);
                if (matched != null) found = matched;
            }
            matcher = altGrab.matcher(text);
            while (matcher.find()) {
                String matched = matchColumnToken(matcher.group(1), ordered);
                if (matched != null) found = matched;
            }
        }
        return found;
    }

    /** 「耗碱量」can resolve to catalog column「耗碱量（显示值）」. */
    private static String matchColumnToken(String raw, List<String> orderedColumns) {
        if (raw == null) return null;
        String token = raw.trim().replaceAll("[。.!！]+$", "").trim();
        if (token.isEmpty()) return null;
        for (String col : orderedColumns) {
            if (col.equalsIgnoreCase(token) || col.equals(token)) return col;
        }
        for (String col : orderedColumns) {
            if (col.contains(token) || token.contains(col)) return col;
        }
        // Strip parenthetical suffixes: 耗碱量（显示值） ↔ 耗碱量
        String stripped = token.replaceAll("[（(][^）)]*[）)]", "").trim();
        if (!stripped.isEmpty() && !stripped.equals(token)) {
            for (String col : orderedColumns) {
                String colBase = col.replaceAll("[（(][^）)]*[）)]", "").trim();
                if (colBase.equalsIgnoreCase(stripped)
                        || col.contains(stripped)
                        || stripped.contains(colBase)) {
                    return col;
                }
            }
        }
        return null;
    }

    private static String pickFileHint(String message, String catalogText) {
        if (message != null) {
            Matcher named = Pattern.compile("(?i)([\\w.\\-]+\\.(?:csv|xlsx))").matcher(message);
            if (named.find()) return named.group(1);
        }
        if (catalogText == null) return null;
        Matcher file = CATALOG_FILE.matcher(catalogText);
        String last = null;
        while (file.find()) last = file.group(1);
        return last;
    }

    static List<String> extractColumns(String catalogText) {
        List<String> columns = new ArrayList<>();
        if (catalogText == null || catalogText.isBlank()) return columns;
        Matcher matcher = CATALOG_COLUMNS.matcher(catalogText);
        while (matcher.find()) {
            String body = matcher.group(1).trim();
            if (body.isEmpty()) continue;
            for (String part : body.split(",")) {
                String col = part.trim().replaceAll("^\"|\"$", "");
                if (!col.isEmpty() && !columns.contains(col)) columns.add(col);
            }
        }
        return columns;
    }

    public AgentDtos.ChartView fromFit(AgentDtos.FitView fit) {
        if (fit == null) return null;
        List<AgentDtos.ChartPointView> observed = new ArrayList<>();
        if (fit.points() != null) {
            for (AgentDtos.FitPointView point : fit.points()) {
                String label =
                        point.recordCode() == null || point.recordCode().isBlank()
                                ? formatNum(point.x())
                                : point.recordCode();
                observed.add(new AgentDtos.ChartPointView(point.x(), point.y(), label));
            }
        }
        List<AgentDtos.ChartSeriesView> series = new ArrayList<>();
        if (!observed.isEmpty()) {
            series.add(new AgentDtos.ChartSeriesView("观测点", observed));
        }
        if (fit.curveSample() != null && !fit.curveSample().isEmpty()) {
            List<AgentDtos.ChartPointView> curve =
                    fit.curveSample().stream()
                            .map(p -> new AgentDtos.ChartPointView(p.x(), p.y(), null))
                            .toList();
            series.add(new AgentDtos.ChartSeriesView("拟合曲线", curve));
        }
        if (series.isEmpty()) return null;
        return new AgentDtos.ChartView("scatter", "拟合图：" + fit.equation(), "x", "y", series);
    }

    private static String formatNum(double value) {
        if (Math.rint(value) == value && Math.abs(value) < 1e9) return String.valueOf((long) value);
        return String.format(Locale.ROOT, "%.4g", value);
    }
}
