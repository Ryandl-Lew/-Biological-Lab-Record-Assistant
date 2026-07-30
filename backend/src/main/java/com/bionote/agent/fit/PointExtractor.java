package com.bionote.agent.fit;

import com.bionote.attachment.AttachmentStorage;
import com.bionote.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PointExtractor {
    private static final int MAX_TABLE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_TABLE_ROWS = 5000;

    private final AgentFitStore fitStore;
    private final ObjectMapper json;
    private final AttachmentStorage storage;
    private final DataFormatter formatter = new DataFormatter();

    public PointExtractor(AgentFitStore fitStore, ObjectMapper json, AttachmentStorage storage) {
        this.fitStore = fitStore;
        this.json = json;
        this.storage = storage;
    }

    public Extracted extract(List<Map<String, Object>> records, FitModels.FitIntent intent) {
        List<FitModels.DataPoint> points = new ArrayList<>();
        List<FitModels.SkipInfo> skipped = new ArrayList<>();
        String source =
                intent.pointSource() == null
                        ? "AUTO"
                        : intent.pointSource().trim().toUpperCase(Locale.ROOT);
        String xSpec = blankToNull(intent.xSpec());
        String ySpec = blankToNull(intent.ySpec());
        if (xSpec == null || ySpec == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "FIT_MISSING_MAPPING", "请指定 x 与 y 对应的字段名或表格列名/列号");
        }
        for (Map<String, Object> record : records) {
            UUID id = UUID.fromString(record.get("id").toString());
            String code = String.valueOf(record.get("code"));
            try {
                if (useTabularAttachment(source, xSpec, ySpec)) {
                    List<FitModels.DataPoint> tablePoints =
                            fromTabularAttachment(
                                    record, id, code, xSpec, ySpec, intent.csvNameHint());
                    if (tablePoints.isEmpty())
                        skipped.add(new FitModels.SkipInfo(id, code, "表格附件中未找到可用数据点"));
                    else points.addAll(tablePoints);
                } else {
                    FitModels.DataPoint point = fromFields(record, id, code, xSpec, ySpec);
                    if (point == null)
                        skipped.add(
                                new FitModels.SkipInfo(
                                        id, code, "字段缺少有效数值: " + xSpec + "/" + ySpec));
                    else points.add(point);
                }
            } catch (ApiException e) {
                skipped.add(new FitModels.SkipInfo(id, code, e.getMessage()));
            } catch (Exception e) {
                skipped.add(new FitModels.SkipInfo(id, code, "抽取失败"));
            }
        }
        return new Extracted(points, skipped);
    }

    private boolean useTabularAttachment(String source, String xSpec, String ySpec) {
        return "CSV_ATTACHMENT".equals(source)
                || "EXCEL_ATTACHMENT".equals(source)
                || "TABLE_ATTACHMENT".equals(source)
                || ("AUTO".equals(source) && looksLikeColumnSpec(xSpec, ySpec));
    }

    private boolean looksLikeColumnSpec(String xSpec, String ySpec) {
        return xSpec.matches("\\d+")
                || ySpec.matches("\\d+")
                || xSpec.toLowerCase(Locale.ROOT).contains("col")
                || ySpec.toLowerCase(Locale.ROOT).contains("col");
    }

    private FitModels.DataPoint fromFields(
            Map<String, Object> record, UUID id, String code, String xSpec, String ySpec) {
        JsonNode values;
        try {
            values = json.readTree(String.valueOf(record.get("field_values_json")));
        } catch (Exception e) {
            return null;
        }
        Double x = asNumber(values, xSpec);
        Double y = asNumber(values, ySpec);
        if (x == null || y == null) return null;
        return new FitModels.DataPoint(x, y, id, code, "TEMPLATE_FIELDS");
    }

    private List<FitModels.DataPoint> fromTabularAttachment(
            Map<String, Object> record,
            UUID id,
            String code,
            String xSpec,
            String ySpec,
            String nameHint) {
        List<Map<String, Object>> attachments = fitStore.findTabularAttachments(id);
        if (attachments.isEmpty()) return List.of();
        Map<String, Object> chosen = attachments.get(0);
        if (nameHint != null && !nameHint.isBlank()) {
            String hint = nameHint.toLowerCase(Locale.ROOT);
            for (Map<String, Object> row : attachments) {
                if (String.valueOf(row.get("original_filename"))
                        .toLowerCase(Locale.ROOT)
                        .contains(hint)) {
                    chosen = row;
                    break;
                }
            }
        }
        long size = ((Number) chosen.get("size_bytes")).longValue();
        if (size <= 0 || size > MAX_TABLE_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FIT_CSV_TOO_LARGE", "表格附件过大或为空");
        }
        byte[] bytes = storage.read(String.valueOf(chosen.get("storage_key")));
        String filename = String.valueOf(chosen.get("original_filename")).toLowerCase(Locale.ROOT);
        if (filename.endsWith(".xlsx")) {
            return parseXlsx(bytes, id, code, xSpec, ySpec, false);
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        return parseCsv(text, id, code, xSpec, ySpec, false);
    }

    private LoadedTable loadTable(Map<String, Object> record, String nameHint) {
        String id = String.valueOf(record.get("id"));
        List<Map<String, Object>> attachments =
                fitStore.findTabularAttachments(UUID.fromString(id));
        if (attachments.isEmpty()) return null;
        Map<String, Object> chosen = attachments.get(0);
        if (nameHint != null && !nameHint.isBlank()) {
            String hint = nameHint.toLowerCase(Locale.ROOT);
            for (Map<String, Object> row : attachments) {
                String name = String.valueOf(row.get("original_filename")).toLowerCase(Locale.ROOT);
                if (name.contains(hint)
                        || hint.contains(name.replace(".xlsx", "").replace(".csv", ""))) {
                    chosen = row;
                    break;
                }
            }
        }
        long size = ((Number) chosen.get("size_bytes")).longValue();
        if (size <= 0 || size > MAX_TABLE_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FIT_CSV_TOO_LARGE", "表格附件过大或为空");
        }
        byte[] bytes = storage.read(String.valueOf(chosen.get("storage_key")));
        String filename = String.valueOf(chosen.get("original_filename"));
        List<String[]> rows;
        if (filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                if (workbook.getNumberOfSheets() <= 0) return null;
                rows = readSheetRows(workbook.getSheetAt(0));
            } catch (Exception e) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, "FIT_EXCEL_PARSE_FAILED", "无法解析 Excel 附件");
            }
        } else {
            rows = csvRows(new String(bytes, StandardCharsets.UTF_8));
        }
        return new LoadedTable(filename, rows);
    }

    private MatrixSlice matrixFromRows(
            List<String[]> rows, List<String> xCols, String yCol, boolean timeToMinutes) {
        if (rows.isEmpty()) return new MatrixSlice(new double[0][], new double[0]);
        String[] header = rows.get(0);
        int start = isHeader(header) ? 1 : 0;
        int[] xIndex = new int[xCols.size()];
        for (int i = 0; i < xCols.size(); i++) {
            xIndex[i] = columnIndex(header, xCols.get(i));
            if (xIndex[i] < 0 && start == 0) xIndex[i] = parseIndex(xCols.get(i), header.length);
            if (xIndex[i] < 0) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "FIT_CSV_COLUMN_MISSING",
                        "找不到自变量列: " + xCols.get(i));
            }
        }
        int yIndex = columnIndex(header, yCol);
        if (yIndex < 0 && start == 0) yIndex = parseIndex(yCol, header.length);
        if (yIndex < 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "FIT_CSV_COLUMN_MISSING", "找不到因变量列: " + yCol);
        }

        boolean[] timeFlags = new boolean[xCols.size()];
        for (int i = 0; i < xCols.size(); i++) {
            timeFlags[i] = timeToMinutes || looksLikeTimeColumn(xCols.get(i));
        }

        double[] timeOrigin = new double[xCols.size()];
        java.util.Arrays.fill(timeOrigin, Double.NaN);
        boolean anyTime = false;
        for (boolean flag : timeFlags) {
            if (flag) {
                anyTime = true;
                break;
            }
        }
        if (anyTime) {
            for (int r = start; r < rows.size(); r++) {
                String[] row = rows.get(r);
                for (int i = 0; i < xCols.size(); i++) {
                    if (!timeFlags[i] || row.length <= xIndex[i]) continue;
                    Double minutes = parseTimeToMinutes(row[xIndex[i]]);
                    if (minutes != null
                            && (Double.isNaN(timeOrigin[i]) || minutes < timeOrigin[i])) {
                        timeOrigin[i] = minutes;
                    }
                }
            }
        }

        List<double[]> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        for (int r = start; r < rows.size(); r++) {
            String[] row = rows.get(r);
            if (row.length <= yIndex) continue;
            Double y = parseDouble(row[yIndex]);
            if (y == null) continue;
            double[] x = new double[xCols.size()];
            boolean ok = true;
            for (int i = 0; i < xCols.size(); i++) {
                if (row.length <= xIndex[i]) {
                    ok = false;
                    break;
                }
                Double value;
                if (timeFlags[i]) {
                    Double minutes = parseTimeToMinutes(row[xIndex[i]]);
                    if (minutes == null || Double.isNaN(timeOrigin[i])) {
                        ok = false;
                        break;
                    }
                    value = minutes - timeOrigin[i];
                } else {
                    value = parseDouble(row[xIndex[i]]);
                }
                if (value == null) {
                    ok = false;
                    break;
                }
                x[i] = value;
            }
            if (!ok) continue;
            xs.add(x);
            ys.add(y);
        }
        double[][] xMat = xs.toArray(new double[0][]);
        double[] yArr = ys.stream().mapToDouble(Double::doubleValue).toArray();
        return new MatrixSlice(xMat, yArr);
    }

    private boolean looksLikeTimeColumn(String name) {
        if (name == null) return false;
        String n = name.trim().toLowerCase(Locale.ROOT);
        return n.equals("时间") || n.equals("time") || n.contains("时刻") || n.equals("采样时间");
    }

    /** Absolute minutes from an arbitrary epoch; callers subtract the minimum. */
    Double parseTimeToMinutes(String raw) {
        if (raw == null) return null;
        String text = raw.trim();
        if (text.isEmpty()) return null;
        Double plain = parseDouble(text);
        if (plain != null) {
            // Excel time serial (fraction of day)
            if (plain >= 0 && plain < 2) return plain * 24 * 60;
            return plain;
        }
        Matcher hourLabel =
                Pattern.compile("(?i)^(\\d+(?:\\.\\d+)?)\\s*(?:h|hr|hrs|hour|hours|小时)$")
                        .matcher(text);
        if (hourLabel.matches()) {
            return Double.parseDouble(hourLabel.group(1)) * 60.0;
        }
        Matcher matcher = Pattern.compile("^(\\d{1,2}):(\\d{2})(?::(\\d{2}))?$").matcher(text);
        if (matcher.matches()) {
            int h = Integer.parseInt(matcher.group(1));
            int m = Integer.parseInt(matcher.group(2));
            int s = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
            return h * 60.0 + m + s / 60.0;
        }
        matcher =
                Pattern.compile(
                                "(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})[ T](\\d{1,2}):(\\d{2})(?::(\\d{2}))?")
                        .matcher(text);
        if (matcher.find()) {
            int hour = Integer.parseInt(matcher.group(4));
            int minute = Integer.parseInt(matcher.group(5));
            int second = matcher.group(6) == null ? 0 : Integer.parseInt(matcher.group(6));
            // Use day-of-month as coarse offset so multi-day runs stay ordered.
            int day = Integer.parseInt(matcher.group(3));
            return day * 24 * 60.0 + hour * 60.0 + minute + second / 60.0;
        }
        return null;
    }

    public List<FitModels.DataPoint> parseCsv(
            String text, UUID id, String code, String xSpec, String ySpec) {
        return parseCsv(text, id, code, xSpec, ySpec, false);
    }

    List<FitModels.DataPoint> parseCsv(
            String text, UUID id, String code, String xSpec, String ySpec, boolean timeToMinutes) {
        List<String[]> rows = csvRows(text);
        if (rows.isEmpty()) return List.of();
        return pointsFromRows(rows, id, code, xSpec, ySpec, "CSV_ATTACHMENT", timeToMinutes);
    }

    public List<FitModels.DataPoint> parseXlsx(
            byte[] bytes, UUID id, String code, String xSpec, String ySpec) {
        return parseXlsx(bytes, id, code, xSpec, ySpec, false);
    }

    List<FitModels.DataPoint> parseXlsx(
            byte[] bytes, UUID id, String code, String xSpec, String ySpec, boolean timeToMinutes) {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            if (workbook.getNumberOfSheets() <= 0) return List.of();
            Sheet sheet = workbook.getSheetAt(0);
            List<String[]> rows = readSheetRows(sheet);
            if (rows.isEmpty()) return List.of();
            return pointsFromRows(rows, id, code, xSpec, ySpec, "EXCEL_ATTACHMENT", timeToMinutes);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "FIT_EXCEL_PARSE_FAILED",
                    "无法解析 Excel 附件（仅支持 .xlsx 首个工作表）");
        }
    }

    /**
     * Build design matrix for multivariate / multi-y fitting from the first matching tabular
     * attachment.
     */
    public MatrixExtract extractMatrix(
            List<Map<String, Object>> records,
            String nameHint,
            List<String> xCols,
            String yCol,
            boolean timeToMinutes) {
        if (records == null || records.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FIT_NO_RECORDS", "未找到符合范围的实验记录");
        }
        List<FitModels.SkipInfo> skipped = new ArrayList<>();
        List<String> used = new ArrayList<>();
        List<double[]> xRows = new ArrayList<>();
        List<Double> yValues = new ArrayList<>();
        for (Map<String, Object> record : records) {
            UUID id = UUID.fromString(record.get("id").toString());
            String code = String.valueOf(record.get("code"));
            try {
                LoadedTable table = loadTable(record, nameHint);
                if (table == null) {
                    skipped.add(new FitModels.SkipInfo(id, code, "未找到 CSV/Excel 附件"));
                    continue;
                }
                MatrixSlice slice = matrixFromRows(table.rows(), xCols, yCol, timeToMinutes);
                if (slice.y.length == 0) {
                    skipped.add(new FitModels.SkipInfo(id, code, "表格中未找到可用数值行（请检查时间/数值列）"));
                    continue;
                }
                for (double[] row : slice.x) xRows.add(row);
                for (double v : slice.y) yValues.add(v);
                if (!used.contains(code)) used.add(code);
            } catch (ApiException e) {
                skipped.add(new FitModels.SkipInfo(id, code, e.getMessage()));
            } catch (Exception e) {
                skipped.add(new FitModels.SkipInfo(id, code, "抽取失败"));
            }
        }
        double[][] x = xRows.toArray(new double[0][]);
        double[] y = yValues.stream().mapToDouble(Double::doubleValue).toArray();
        return new MatrixExtract(xCols, yCol, x, y, used, skipped);
    }

    /**
     * Peek first-row headers (or synthetic col0.. for headerless sheets). Best-effort, never
     * throws.
     */
    public List<String> peekHeaders(byte[] bytes, String filename) {
        try {
            String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
            List<String[]> rows;
            if (name.endsWith(".xlsx")) {
                try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                    if (workbook.getNumberOfSheets() <= 0) return List.of();
                    rows = readSheetRows(workbook.getSheetAt(0));
                }
            } else {
                String text = new String(bytes, StandardCharsets.UTF_8);
                rows = csvRows(text);
            }
            if (rows.isEmpty()) return List.of();
            String[] first = rows.get(0);
            if (isHeader(first)) {
                List<String> headers = new ArrayList<>();
                for (String cell : first) {
                    if (cell != null && !cell.isBlank()) headers.add(cell.trim());
                }
                return headers;
            }
            List<String> synthetic = new ArrayList<>();
            for (int i = 0; i < first.length; i++) synthetic.add("col" + i);
            return synthetic;
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Read all rows from an XLSX byte array for preview purposes. Best-effort, never throws. */
    public List<String[]> readXlsxRows(byte[] bytes) {
        try {
            try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                if (workbook.getNumberOfSheets() <= 0) return List.of();
                return readSheetRows(workbook.getSheetAt(0));
            }
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String[]> readSheetRows(Sheet sheet) {
        List<String[]> rows = new ArrayList<>();
        int lastRow = Math.min(sheet.getLastRowNum(), MAX_TABLE_ROWS);
        for (int r = 0; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            int lastCell = row.getLastCellNum();
            if (lastCell <= 0) continue;
            String[] cells = new String[lastCell];
            boolean any = false;
            for (int c = 0; c < lastCell; c++) {
                String value = cellText(row.getCell(c));
                cells[c] = value;
                if (value != null && !value.isBlank()) any = true;
            }
            if (any) rows.add(cells);
            if (rows.size() > MAX_TABLE_ROWS) break;
        }
        return rows;
    }

    private String cellText(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            if (Double.isFinite(value) && value == Math.rint(value) && Math.abs(value) < 1e15) {
                return Long.toString((long) value);
            }
        }
        return formatter.formatCellValue(cell).trim();
    }

    private List<String[]> csvRows(String text) {
        String[] lines = text.split("\\R");
        List<String[]> rows = new ArrayList<>();
        for (String line : lines) {
            if (line == null || line.isBlank() || line.startsWith("#")) continue;
            rows.add(splitCsvLine(line));
            if (rows.size() > MAX_TABLE_ROWS) break;
        }
        return rows;
    }

    private List<FitModels.DataPoint> pointsFromRows(
            List<String[]> rows,
            UUID id,
            String code,
            String xSpec,
            String ySpec,
            String sourceLabel,
            boolean timeToMinutes) {
        MatrixSlice slice =
                matrixFromRows(
                        rows, List.of(xSpec), ySpec, timeToMinutes || looksLikeTimeColumn(xSpec));
        List<FitModels.DataPoint> points = new ArrayList<>();
        for (int i = 0; i < slice.y.length; i++) {
            points.add(new FitModels.DataPoint(slice.x[i][0], slice.y[i], id, code, sourceLabel));
        }
        return points;
    }

    private boolean isHeader(String[] cells) {
        int numeric = 0;
        for (String cell : cells) if (parseDouble(cell) != null) numeric++;
        return numeric * 2 < cells.length;
    }

    private int columnIndex(String[] header, String spec) {
        if (spec.matches("\\d+")) return parseIndex(spec, header.length);
        for (int i = 0; i < header.length; i++) {
            if (header[i].trim().equalsIgnoreCase(spec.trim())) return i;
        }
        return -1;
    }

    private int parseIndex(String spec, int width) {
        try {
            int index = Integer.parseInt(spec.trim());
            return index >= 0 && index < width ? index : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private Double asNumber(JsonNode values, String key) {
        if (values == null || key == null) return null;
        JsonNode direct = values.get(key);
        if (direct == null) {
            var fields = values.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                if (entry.getKey().equalsIgnoreCase(key)) {
                    direct = entry.getValue();
                    break;
                }
            }
        }
        if (direct == null || direct.isNull()) return null;
        if (direct.isNumber()) return direct.asDouble();
        return parseDouble(direct.asText());
    }

    private Double parseDouble(String raw) {
        if (raw == null) return null;
        String text = raw.trim().replace(",", "");
        if (text.isEmpty()) return null;
        try {
            double value = Double.parseDouble(text);
            return Double.isFinite(value) ? value : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String[] splitCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if ((c == ',' || c == '\t') && !quoted) {
                cells.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString().trim());
        return cells.toArray(String[]::new);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record Extracted(List<FitModels.DataPoint> points, List<FitModels.SkipInfo> skipped) {}

    public record MatrixExtract(
            List<String> xColumns,
            String yColumn,
            double[][] x,
            double[] y,
            List<String> usedRecordCodes,
            List<FitModels.SkipInfo> skipped) {}

    private record LoadedTable(String filename, List<String[]> rows) {}

    private record MatrixSlice(double[][] x, double[] y) {}
}
