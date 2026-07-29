package com.bionote.agent.fit;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class FitModels {
    private FitModels() {}

    public record DataPoint(double x, double y, UUID recordId, String recordCode, String source) {}

    public record SkipInfo(UUID recordId, String recordCode, String reason) {}

    public record ModelComparison(String equation, double rSquared, double rmse, int n, boolean selected) {}

    public record FitIntent(
            boolean fitRequested,
            String equation,
            String xSpec,
            String ySpec,
            String pointSource,
            String csvNameHint,
            List<String> recordCodes,
            List<String> statuses,
            String experimentType,
            String keyword,
            String missingPrompt
    ) {}

    public record FitProposal(
            boolean fitRequested,
            String equation,
            boolean autoCompare,
            List<String> candidateIds,
            String xSpec,
            String ySpec,
            String pointSource,
            String csvNameHint,
            List<String> recordCodes,
            List<String> statuses,
            String experimentType,
            String keyword,
            String rationale,
            String missingPrompt,
            boolean multivariate,
            boolean timeToMinutes
    ) {
        public FitIntent toIntent() {
            // Univariate extract uses the first x/y when multiple are provided.
            String x = firstSpec(xSpec);
            String y = firstSpec(ySpec);
            return new FitIntent(true, equation, x, y, pointSource, csvNameHint,
                    recordCodes == null ? List.of() : recordCodes,
                    statuses == null ? List.of() : statuses,
                    experimentType, keyword, missingPrompt);
        }

        private static String firstSpec(String spec) {
            if (spec == null || spec.isBlank()) return null;
            String[] parts = spec.split("[,，、;/|]+");
            return parts.length == 0 ? null : parts[0].trim();
        }
    }

    public record FitResult(
            String equation,
            Map<String, Double> parameters,
            double rSquared,
            double rmse,
            int n,
            List<String> usedRecordCodes,
            List<SkipInfo> skipped,
            List<Map<String, Double>> curveSample,
            List<Map<String, Object>> points,
            List<ModelComparison> comparisons
    ) {
        public FitResult {
            comparisons = comparisons == null ? List.of() : List.copyOf(comparisons);
        }
    }

    public record CatalogFile(String filename, List<String> columns) {}

    public record CatalogRecord(
            String code,
            String title,
            String status,
            String experimentType,
            List<String> numericFieldKeys,
            List<CatalogFile> tableFiles
    ) {
        public List<String> csvFilenames() {
            return tableFiles == null ? List.of() : tableFiles.stream().map(CatalogFile::filename).toList();
        }
    }
}
