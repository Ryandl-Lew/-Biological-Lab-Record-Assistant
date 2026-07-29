package com.bionote.agent.fit;

import com.bionote.attachment.AttachmentStorage;
import com.bionote.common.ApiException;
import com.bionote.project.ProjectMemberStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class CurveFitService {
    private static final int MAX_RECORDS = 50;
    private static final int CATALOG_LIMIT = 30;
    private static final int MAX_PEEK_BYTES = 2 * 1024 * 1024;

    private final AgentFitStore fitStore;
    private final ProjectMemberStore members;
    private final FitIntentParser intents;
    private final PointExtractor extractor;
    private final CurveFitEngine engine;
    private final MultivariateFitEngine multivariateEngine;
    private final ObjectMapper json;
    private final AttachmentStorage storage;

    public CurveFitService(AgentFitStore fitStore, ProjectMemberStore members, FitIntentParser intents, PointExtractor extractor,
                           CurveFitEngine engine, MultivariateFitEngine multivariateEngine,
                           ObjectMapper json, AttachmentStorage storage) {
        this.fitStore = fitStore;
        this.members = members;
        this.intents = intents;
        this.extractor = extractor;
        this.engine = engine;
        this.multivariateEngine = multivariateEngine;
        this.json = json;
        this.storage = storage;
    }

    public FitModels.FitIntent parseIntent(String message) {
        return intents.parse(message);
    }

    public boolean looksLikeFitRequest(String message) {
        if (message == null || message.isBlank()) return false;
        String text = message.toLowerCase(Locale.ROOT);
        return message.contains("拟合") || text.contains("fit") || message.contains("标曲")
                || message.contains("标准曲线") || message.contains("剂量反应") || message.contains("回归")
                || message.contains("自变量") || message.contains("因变量")
                || text.contains("curve fit") || text.contains("dose-response");
    }

    public List<FitModels.CatalogRecord> buildFitCatalog(UUID projectId) {
        List<Map<String,Object>> records=fitStore.findCatalogRecords(projectId,CATALOG_LIMIT);
        List<FitModels.CatalogRecord> catalog = new ArrayList<>();
        for (Map<String, Object> record : records) {
            String id = String.valueOf(record.get("id"));
            List<String> numericKeys = numericFieldKeys(String.valueOf(record.get("field_values_json")));
            List<FitModels.CatalogFile> tableFiles = new ArrayList<>();
            List<Map<String,Object>> attachments=fitStore.findTabularAttachments(UUID.fromString(id));
            for (Map<String, Object> attachment : attachments) {
                String filename = String.valueOf(attachment.get("original_filename"));
                List<String> columns = List.of();
                try {
                    long size = ((Number) attachment.get("size_bytes")).longValue();
                    if (size > 0 && size <= MAX_PEEK_BYTES) {
                        byte[] bytes = storage.read(String.valueOf(attachment.get("storage_key")));
                        columns = extractor.peekHeaders(bytes, filename);
                    }
                } catch (Exception ignored) {
                    // catalog enrichment is best-effort
                }
                tableFiles.add(new FitModels.CatalogFile(filename, columns));
            }
            catalog.add(new FitModels.CatalogRecord(
                    String.valueOf(record.get("code")),
                    String.valueOf(record.get("title")),
                    String.valueOf(record.get("status")),
                    String.valueOf(record.get("experiment_type")),
                    numericKeys,
                    tableFiles));
        }
        return catalog;
    }

    public String formatFitCatalog(List<FitModels.CatalogRecord> catalog) {
        StringBuilder sb = new StringBuilder();
        for (FitModels.CatalogRecord row : catalog) {
            sb.append("- ").append(row.code()).append(" | ").append(row.title())
                    .append(" | ").append(row.status()).append(" | ").append(row.experimentType())
                    .append(" | fields=").append(row.numericFieldKeys())
                    .append(" | tables=");
            if (row.tableFiles() == null || row.tableFiles().isEmpty()) {
                sb.append("[]");
            } else {
                sb.append('[');
                for (int i = 0; i < row.tableFiles().size(); i++) {
                    FitModels.CatalogFile file = row.tableFiles().get(i);
                    if (i > 0) sb.append("; ");
                    sb.append("file=\"").append(file.filename()).append("\" columns=").append(file.columns());
                }
                sb.append(']');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    public FitModels.FitResult fit(UUID actor, UUID projectId, FitModels.FitIntent intent) {
        requireMember(actor, projectId);
        if (intent == null || !intent.fitRequested()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FIT_NOT_REQUESTED", "当前消息未识别为拟合请求");
        }
        if (intent.missingPrompt() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FIT_MISSING_INPUT", intent.missingPrompt());
        }
        ExpressionParser.CompiledEquation equation = ExpressionParser.parseEquation(intent.equation());
        List<Map<String, Object>> records = loadRecords(projectId, intent);
        if (records.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FIT_NO_RECORDS", "未找到符合范围的实验记录");
        }
        PointExtractor.Extracted extracted = extractor.extract(records, intent);
        return engine.fit(equation, extracted.points(), extracted.skipped());
    }

    public FitModels.FitResult fitProposal(UUID actor, UUID projectId, FitModels.FitProposal proposal) {
        List<FitModels.FitResult> results = fitProposalAll(actor, projectId, proposal);
        return results.get(0);
    }

    /** Extract numeric points for charting without running a regression. */
    public List<FitModels.DataPoint> extractPlotPoints(UUID actor, UUID projectId, FitModels.FitProposal proposal) {
        requireMember(actor, projectId);
        if (proposal == null || proposal.xSpec() == null || proposal.ySpec() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CHART_MISSING_MAPPING", "请指定横轴与纵轴列名");
        }
        FitModels.FitIntent intent = proposal.toIntent();
        if (intent.equation() == null || intent.equation().isBlank()) {
            intent = new FitModels.FitIntent(
                    true, "y=a+b*x", intent.xSpec(), intent.ySpec(), intent.pointSource(), intent.csvNameHint(),
                    intent.recordCodes(), intent.statuses(), intent.experimentType(), intent.keyword(), null);
        }
        List<Map<String, Object>> records = loadRecords(projectId, intent);
        if (records.isEmpty() && hasRecordFilters(intent)) {
            records = loadRecords(projectId, clearRecordFilters(intent));
        }
        if (records.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CHART_NO_RECORDS", "未找到符合范围的实验记录");
        }
        boolean timeToMinutes = proposal.timeToMinutes()
                || CurveFitService.splitSpecs(proposal.xSpec()).stream()
                .anyMatch(c -> c.contains("时间") || c.equalsIgnoreCase("time"));
        List<String> xCols = splitSpecs(proposal.xSpec());
        List<String> yCols = splitSpecs(proposal.ySpec());
        if (xCols.size() == 1 && yCols.size() == 1 && (timeToMinutes
                || "CSV_ATTACHMENT".equalsIgnoreCase(blankToAuto(proposal.pointSource()))
                || "EXCEL_ATTACHMENT".equalsIgnoreCase(blankToAuto(proposal.pointSource()))
                || "TABLE_ATTACHMENT".equalsIgnoreCase(blankToAuto(proposal.pointSource())))) {
            try {
                PointExtractor.MatrixExtract matrix = extractor.extractMatrix(
                        records, proposal.csvNameHint(), xCols, yCols.get(0), timeToMinutes);
                List<FitModels.DataPoint> points = new ArrayList<>();
                for (int i = 0; i < matrix.y().length; i++) {
                    points.add(new FitModels.DataPoint(
                            matrix.x()[i][0], matrix.y()[i], null,
                            matrix.usedRecordCodes().isEmpty() ? "" : matrix.usedRecordCodes().get(0),
                            "TABLE_ATTACHMENT"));
                }
                if (!points.isEmpty()) return points;
            } catch (ApiException ignored) {
                // fall through to generic extractor
            }
        }
        PointExtractor.Extracted extracted = extractor.extract(records, intent);
        return extracted.points();
    }

    private static String blankToAuto(String value) {
        return value == null || value.isBlank() ? "AUTO" : value.trim();
    }

    public List<FitModels.FitResult> fitProposalAll(UUID actor, UUID projectId, FitModels.FitProposal proposal) {
        requireMember(actor, projectId);
        if (proposal == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FIT_MISSING_INPUT", "缺少确认的拟合方案");
        }
        if (proposal.missingPrompt() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FIT_MISSING_INPUT", proposal.missingPrompt());
        }
        List<String> xCols = splitSpecs(proposal.xSpec());
        List<String> yCols = splitSpecs(proposal.ySpec());
        if (xCols.isEmpty() || yCols.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FIT_MISSING_MAPPING", "请指定自变量与因变量列名");
        }
        boolean useMultivariate = proposal.multivariate() || xCols.size() > 1 || looksLikeMultivariateEquation(proposal.equation());
        boolean timeToMinutes = proposal.timeToMinutes()
                || xCols.stream().anyMatch(c -> c.contains("时间") || c.equalsIgnoreCase("time"));
        FitModels.FitIntent intent = proposal.toIntent();
        List<Map<String, Object>> records = loadRecords(projectId, intent);
        if (records.isEmpty() && hasRecordFilters(intent)) {
            // Stale/LLM-invented code or status filters often exclude the only attachment record.
            records = loadRecords(projectId, clearRecordFilters(intent));
        }
        if (records.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FIT_NO_RECORDS", "未找到符合范围的实验记录");
        }

        if (useMultivariate || timeToMinutes || yCols.size() > 1) {
            List<FitModels.FitResult> results = new ArrayList<>();
            for (String yCol : yCols) {
                PointExtractor.MatrixExtract matrix = extractor.extractMatrix(
                        records, proposal.csvNameHint(), xCols, yCol, timeToMinutes);
                if (matrix.y().length < 3) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "FIT_INSUFFICIENT_POINTS",
                            "因变量「" + yCol + "」有效样本不足（需要至少 3 行数值）");
                }
                if (xCols.size() == 1 && !useMultivariate) {
                    List<FitModels.DataPoint> points = new ArrayList<>();
                    for (int i = 0; i < matrix.y().length; i++) {
                        points.add(new FitModels.DataPoint(matrix.x()[i][0], matrix.y()[i], null,
                                matrix.usedRecordCodes().isEmpty() ? "" : matrix.usedRecordCodes().get(0),
                                "TABLE_ATTACHMENT"));
                    }
                    String equation = proposal.equation();
                    if (equation == null || equation.isBlank() || looksLikeMultivariateEquation(equation)) {
                        equation = "y=a+b*x";
                    }
                    try {
                        results.add(engine.fit(ExpressionParser.parseEquation(equation), points, matrix.skipped()));
                    } catch (ApiException e) {
                        if ("FIT_INVALID_EQUATION".equals(e.code())) {
                            results.add(engine.fit(ExpressionParser.parseEquation("y=a+b*x"), points, matrix.skipped()));
                        } else throw e;
                    }
                } else {
                    results.add(multivariateEngine.fit(xCols, yCol, matrix.x(), matrix.y(), matrix.skipped(), matrix.usedRecordCodes()));
                }
            }
            return results;
        }

        PointExtractor.Extracted extracted = extractor.extract(records, intent);
        if (proposal.autoCompare()) {
            List<ExpressionParser.CompiledEquation> equations = new ArrayList<>();
            List<String> ids = proposal.candidateIds() == null || proposal.candidateIds().isEmpty()
                    ? FitMethodCatalog.all().stream().map(FitMethodCatalog.Method::id).toList()
                    : proposal.candidateIds();
            for (String id : ids) {
                FitMethodCatalog.Method method = FitMethodCatalog.byId(id);
                String eq = method != null ? method.equation() : id;
                try {
                    equations.add(ExpressionParser.parseEquation(eq));
                } catch (ApiException ignored) {
                    // skip invalid candidate
                }
            }
            if (equations.isEmpty()) {
                for (String eq : FitMethodCatalog.allEquations()) {
                    equations.add(ExpressionParser.parseEquation(eq));
                }
            }
            return List.of(engine.fitBest(equations, extracted.points(), extracted.skipped()));
        }
        if (proposal.equation() == null || proposal.equation().isBlank() || looksLikeMultivariateEquation(proposal.equation())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FIT_INVALID_EQUATION",
                    "当前方案方程无法用于单变量拟合。请改用预置方程（如 y=a+b*x），或明确自变量/因变量列以启用多元回归。");
        }
        ExpressionParser.CompiledEquation equation = ExpressionParser.parseEquation(proposal.equation());
        return List.of(engine.fit(equation, extracted.points(), extracted.skipped()));
    }

    public static List<String> splitSpecs(String spec) {
        if (spec == null || spec.isBlank()) return List.of();
        List<String> values = new ArrayList<>();
        for (String part : spec.split("[,，、;/|]+")) {
            String value = part.trim();
            if (!value.isEmpty()) values.add(value);
        }
        return values;
    }

    public static boolean looksLikeMultivariateEquation(String equation) {
        if (equation == null) return false;
        String text = equation.toLowerCase(Locale.ROOT);
        return text.contains("...") || text.contains("…") || text.contains("b1") || text.contains("x1")
                || text.contains("b2") || text.contains("x2") || text.contains("线性组合")
                || text.contains("多元") || text.contains("b_*") || text.matches(".*\\bb\\d+\\b.*");
    }

    private boolean hasRecordFilters(FitModels.FitIntent intent) {
        return (intent.recordCodes() != null && !intent.recordCodes().isEmpty())
                || (intent.statuses() != null && !intent.statuses().isEmpty())
                || (intent.experimentType() != null && !intent.experimentType().isBlank())
                || (intent.keyword() != null && !intent.keyword().isBlank());
    }

    private FitModels.FitIntent clearRecordFilters(FitModels.FitIntent intent) {
        return new FitModels.FitIntent(
                intent.fitRequested(), intent.equation(), intent.xSpec(), intent.ySpec(),
                intent.pointSource(), intent.csvNameHint(),
                List.of(), List.of(), null, null, intent.missingPrompt());
    }

    private List<Map<String, Object>> loadRecords(UUID projectId, FitModels.FitIntent intent) {
        return fitStore.findRecords(projectId,intent,MAX_RECORDS);
    }

    private List<String> numericFieldKeys(String fieldValuesJson) {
        List<String> keys = new ArrayList<>();
        try {
            JsonNode node = json.readTree(fieldValuesJson == null ? "{}" : fieldValuesJson);
            var fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                JsonNode value = entry.getValue();
                if (value != null && value.isNumber()) keys.add(entry.getKey());
                else if (value != null && value.isTextual()) {
                    try {
                        Double.parseDouble(value.asText().trim().replace(",", ""));
                        keys.add(entry.getKey());
                    } catch (Exception ignored) {
                        // non-numeric
                    }
                }
            }
        } catch (Exception ignored) {
            // best-effort catalog
        }
        return keys;
    }

    private void requireMember(UUID actor, UUID projectId) {
        if (members.findRole(projectId,actor).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource not found or inaccessible");
        }
    }
}
