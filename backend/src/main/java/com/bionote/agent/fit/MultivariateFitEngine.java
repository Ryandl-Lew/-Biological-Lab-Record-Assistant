package com.bionote.agent.fit;

import com.bionote.common.ApiException;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Multivariate OLS with automatic dropping of constant / linearly dependent predictors
 * so fermentation tables with near-collinear process variables still produce a fit.
 */
@Component
public class MultivariateFitEngine {
    private static final double VAR_EPS = 1e-12;
    private static final double CORR_EPS = 1.0 - 1e-9;

    public FitModels.FitResult fit(List<String> xNames, String yName, double[][] xRows, double[] y,
                                   List<FitModels.SkipInfo> skipped, List<String> usedRecordCodes) {
        if (xNames == null || xNames.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FIT_MISSING_MAPPING", "多元回归需要至少一个自变量列");
        }
        if (y == null || y.length < 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FIT_INSUFFICIENT_POINTS",
                    "多元回归至少需要 3 个有效样本行");
        }
        if (xRows == null || xRows.length != y.length) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FIT_FAILED", "自变量与因变量行数不一致");
        }

        PrunedDesign pruned = prunePredictors(xNames, xRows, y.length);
        if (pruned.names.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FIT_FAILED",
                    "自变量在有效样本上均为常数或完全共线，无法做多元回归。请减少自变量或检查数据。");
        }
        int need = pruned.names.size() + 2;
        if (y.length < need) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FIT_INSUFFICIENT_POINTS",
                    "有效样本 " + y.length + " 行，相对保留的 " + pruned.names.size()
                            + " 个自变量仍不足（至少需要 " + need + " 行）。已尝试剔除常数/共线列："
                            + (pruned.dropped.isEmpty() ? "无" : String.join("、", pruned.dropped)));
        }

        try {
            OLSMultipleLinearRegression ols = new OLSMultipleLinearRegression();
            ols.newSampleData(y, pruned.x);
            double[] beta = ols.estimateRegressionParameters();
            double rSquared = ols.calculateRSquared();
            double[] residuals = ols.estimateResiduals();
            double ssRes = 0;
            for (double r : residuals) ssRes += r * r;
            double rmse = Math.sqrt(ssRes / y.length);

            Map<String, Double> parameters = new LinkedHashMap<>();
            parameters.put("a", beta[0]);
            StringBuilder equation = new StringBuilder(yName).append(" = a");
            for (int i = 0; i < pruned.names.size(); i++) {
                String key = "b_" + sanitize(pruned.names.get(i));
                parameters.put(key, beta[i + 1]);
                equation.append(" + ").append(key).append("*").append(pruned.names.get(i));
            }
            if (!pruned.dropped.isEmpty()) {
                equation.append("  [已剔除常数/共线列: ").append(String.join("、", pruned.dropped)).append("]");
            }

            List<Map<String, Object>> pointViews = new ArrayList<>();
            for (int i = 0; i < Math.min(y.length, 200); i++) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("x", pruned.x[i][0]);
                row.put("y", y[i]);
                row.put("recordCode", usedRecordCodes.isEmpty() ? "" : usedRecordCodes.get(0));
                row.put("source", "TABLE_MULTIVARIATE");
                pointViews.add(row);
            }

            return new FitModels.FitResult(
                    equation.toString(),
                    parameters,
                    rSquared,
                    rmse,
                    y.length,
                    usedRecordCodes,
                    skipped == null ? List.of() : List.copyOf(skipped),
                    List.of(),
                    pointViews,
                    List.of()
            );
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            String detail = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
            if (detail.contains("singular")) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "FIT_FAILED",
                        "多元线性回归失败：自变量仍存在完全共线（或样本相对自变量过少）。"
                                + "已剔除：" + (pruned.dropped.isEmpty() ? "无" : String.join("、", pruned.dropped))
                                + "。建议减少高度相关的过程变量后再试。");
            }
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "FIT_FAILED",
                    "多元线性回归失败：" + (e.getMessage() == null ? "请检查数据是否含非数值或共线列" : e.getMessage()));
        }
    }

    /**
     * Drop zero-variance columns, then greedily keep a full-rank subset (and cap by n-2).
     */
    PrunedDesign prunePredictors(List<String> xNames, double[][] xRows, int n) {
        int p = xNames.size();
        List<Integer> candidates = new ArrayList<>();
        List<String> dropped = new ArrayList<>();
        for (int j = 0; j < p; j++) {
            if (variance(xRows, j) <= VAR_EPS) {
                dropped.add(xNames.get(j) + "(常数列)");
            } else {
                candidates.add(j);
            }
        }

        List<Integer> keptIdx = new ArrayList<>();
        int maxKeep = Math.max(0, n - 2);
        for (int j : candidates) {
            if (keptIdx.size() >= maxKeep) {
                dropped.add(xNames.get(j) + "(样本不足未纳入)");
                continue;
            }
            if (isNearlyCollinear(xRows, keptIdx, j)) {
                dropped.add(xNames.get(j) + "(共线)");
                continue;
            }
            keptIdx.add(j);
        }

        List<String> keptNames = new ArrayList<>();
        double[][] keptX = new double[n][keptIdx.size()];
        for (int k = 0; k < keptIdx.size(); k++) {
            int j = keptIdx.get(k);
            keptNames.add(xNames.get(j));
            for (int i = 0; i < n; i++) keptX[i][k] = xRows[i][j];
        }
        return new PrunedDesign(keptNames, keptX, dropped);
    }

    private boolean isNearlyCollinear(double[][] xRows, List<Integer> keptIdx, int candidate) {
        if (keptIdx.isEmpty()) return false;
        int n = xRows.length;
        // Exact duplicate / |corr|≈1 with any kept column
        for (int kept : keptIdx) {
            if (Math.abs(pearson(xRows, kept, candidate)) >= CORR_EPS) return true;
        }
        // Rank check via trying OLS of candidate on kept columns (+intercept): R²≈1 ⇒ dependent
        if (keptIdx.size() + 2 > n) return true;
        try {
            double[] y = new double[n];
            double[][] x = new double[n][keptIdx.size()];
            for (int i = 0; i < n; i++) {
                y[i] = xRows[i][candidate];
                for (int k = 0; k < keptIdx.size(); k++) x[i][k] = xRows[i][keptIdx.get(k)];
            }
            OLSMultipleLinearRegression ols = new OLSMultipleLinearRegression();
            ols.newSampleData(y, x);
            return ols.calculateRSquared() >= CORR_EPS;
        } catch (Exception e) {
            return true;
        }
    }

    private double variance(double[][] xRows, int col) {
        int n = xRows.length;
        double mean = 0;
        for (double[] row : xRows) mean += row[col];
        mean /= n;
        double var = 0;
        for (double[] row : xRows) {
            double d = row[col] - mean;
            var += d * d;
        }
        return var / n;
    }

    private double pearson(double[][] xRows, int a, int b) {
        int n = xRows.length;
        double ma = 0, mb = 0;
        for (double[] row : xRows) {
            ma += row[a];
            mb += row[b];
        }
        ma /= n;
        mb /= n;
        double num = 0, da = 0, db = 0;
        for (double[] row : xRows) {
            double xa = row[a] - ma;
            double xb = row[b] - mb;
            num += xa * xb;
            da += xa * xa;
            db += xb * xb;
        }
        if (da <= VAR_EPS || db <= VAR_EPS) return 1.0;
        return num / Math.sqrt(da * db);
    }

    private String sanitize(String name) {
        String value = name == null ? "x" : name.replaceAll("[^A-Za-z0-9_\\u4e00-\\u9fff]", "_");
        return value.isBlank() ? "x" : value;
    }

    record PrunedDesign(List<String> names, double[][] x, List<String> dropped) {}
}
