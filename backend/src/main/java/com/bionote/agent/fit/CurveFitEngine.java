package com.bionote.agent.fit;

import com.bionote.common.ApiException;
import org.apache.commons.math3.analysis.ParametricUnivariateFunction;
import org.apache.commons.math3.fitting.SimpleCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CurveFitEngine {
    public FitModels.FitResult fit(ExpressionParser.CompiledEquation equation, List<FitModels.DataPoint> points, List<FitModels.SkipInfo> skipped) {
        return fitInternal(equation, points, skipped, List.of());
    }

    public FitModels.FitResult fitBest(List<ExpressionParser.CompiledEquation> equations,
                                       List<FitModels.DataPoint> points,
                                       List<FitModels.SkipInfo> skipped) {
        if (equations == null || equations.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FIT_INVALID_EQUATION", "未提供候选方程");
        }
        if (points == null || points.size() < 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FIT_INSUFFICIENT_POINTS", "至少需要 3 个有效数据点才能拟合");
        }
        List<FitModels.FitResult> successes = new ArrayList<>();
        List<FitModels.ModelComparison> comparisons = new ArrayList<>();
        for (ExpressionParser.CompiledEquation equation : equations) {
            try {
                List<FitModels.DataPoint> usable = filterForEquation(equation, points);
                FitModels.FitResult result = fitInternal(equation, usable, skipped, List.of());
                successes.add(result);
                comparisons.add(new FitModels.ModelComparison(result.equation(), result.rSquared(), result.rmse(), result.n(), false));
            } catch (ApiException ignored) {
                comparisons.add(new FitModels.ModelComparison(equation.equation(), Double.NaN, Double.NaN, 0, false));
            }
        }
        if (successes.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "FIT_FAILED", "所有候选方程均未能完成拟合，请检查数据或缩小候选范围");
        }
        successes.sort(Comparator
                .comparingDouble(FitModels.FitResult::rSquared).reversed()
                .thenComparingDouble(FitModels.FitResult::rmse));
        FitModels.FitResult best = successes.get(0);
        List<FitModels.ModelComparison> marked = new ArrayList<>();
        for (FitModels.ModelComparison row : comparisons) {
            boolean selected = row.equation().equals(best.equation())
                    && Double.isFinite(row.rSquared())
                    && Math.abs(row.rSquared() - best.rSquared()) < 1e-12
                    && Math.abs(row.rmse() - best.rmse()) < 1e-12;
            marked.add(new FitModels.ModelComparison(row.equation(), row.rSquared(), row.rmse(), row.n(), selected));
        }
        // Ensure exactly one selected flag for the winning equation.
        boolean anySelected = marked.stream().anyMatch(FitModels.ModelComparison::selected);
        if (!anySelected) {
            for (int i = 0; i < marked.size(); i++) {
                FitModels.ModelComparison row = marked.get(i);
                if (row.equation().equals(best.equation()) && Double.isFinite(row.rSquared())) {
                    marked.set(i, new FitModels.ModelComparison(row.equation(), row.rSquared(), row.rmse(), row.n(), true));
                    break;
                }
            }
        }
        return new FitModels.FitResult(best.equation(), best.parameters(), best.rSquared(), best.rmse(), best.n(),
                best.usedRecordCodes(), best.skipped(), best.curveSample(), best.points(), marked);
    }

    private FitModels.FitResult fitInternal(ExpressionParser.CompiledEquation equation,
                                            List<FitModels.DataPoint> points,
                                            List<FitModels.SkipInfo> skipped,
                                            List<FitModels.ModelComparison> comparisons) {
        if (points == null || points.size() < 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FIT_INSUFFICIENT_POINTS", "至少需要 3 个有效数据点才能拟合");
        }
        if (points.size() < equation.parameters().size() + 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FIT_INSUFFICIENT_POINTS",
                    "数据点不足以估计 " + equation.parameters().size() + " 个参数");
        }
        WeightedObservedPoints observations = new WeightedObservedPoints();
        for (FitModels.DataPoint point : points) {
            if (!Double.isFinite(point.x()) || !Double.isFinite(point.y())) continue;
            observations.add(point.x(), point.y());
        }
        if (observations.toList().size() < 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FIT_INSUFFICIENT_POINTS", "至少需要 3 个有限数据点");
        }

        double[] guess = initialGuess(equation.parameters().size(), points);
        ParametricUnivariateFunction function = new ParametricUnivariateFunction() {
            @Override public double value(double x, double... parameters) {
                return equation.value(x, parameters);
            }

            @Override public double[] gradient(double x, double... parameters) {
                double[] grad = new double[parameters.length];
                double base = equation.value(x, parameters);
                double eps = 1e-6;
                for (int i = 0; i < parameters.length; i++) {
                    double[] shifted = parameters.clone();
                    double step = Math.max(eps, Math.abs(parameters[i]) * eps);
                    shifted[i] = parameters[i] + step;
                    grad[i] = (equation.value(x, shifted) - base) / step;
                }
                return grad;
            }
        };

        double[] fitted;
        try {
            fitted = SimpleCurveFitter.create(function, guess)
                    .withMaxIterations(200)
                    .fit(observations.toList());
        } catch (Exception e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "FIT_FAILED", "曲线拟合未收敛，请检查方程或数据");
        }

        Map<String, Double> parameters = new LinkedHashMap<>();
        for (int i = 0; i < equation.parameters().size(); i++) {
            parameters.put(equation.parameters().get(i), fitted[i]);
        }

        double ssRes = 0, ssTot = 0, meanY = 0;
        for (FitModels.DataPoint point : points) meanY += point.y();
        meanY /= points.size();
        for (FitModels.DataPoint point : points) {
            double pred = equation.value(point.x(), fitted);
            if (!Double.isFinite(pred)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "FIT_FAILED", "拟合预测出现非有限值");
            }
            ssRes += Math.pow(point.y() - pred, 2);
            ssTot += Math.pow(point.y() - meanY, 2);
        }
        double rmse = Math.sqrt(ssRes / points.size());
        double rSquared = ssTot == 0 ? 1.0 : 1.0 - ssRes / ssTot;

        List<String> used = new ArrayList<>();
        List<Map<String, Object>> pointViews = new ArrayList<>();
        for (FitModels.DataPoint point : points) {
            if (point.recordCode() != null && !used.contains(point.recordCode())) used.add(point.recordCode());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("x", point.x());
            row.put("y", point.y());
            row.put("recordCode", point.recordCode());
            row.put("source", point.source());
            pointViews.add(row);
        }

        double minX = points.stream().mapToDouble(FitModels.DataPoint::x).min().orElse(0);
        double maxX = points.stream().mapToDouble(FitModels.DataPoint::x).max().orElse(1);
        List<Map<String, Double>> curve = new ArrayList<>();
        int samples = 25;
        for (int i = 0; i < samples; i++) {
            double x = minX + (maxX - minX) * i / Math.max(1, samples - 1);
            double y = equation.value(x, fitted);
            if (!Double.isFinite(y)) continue;
            curve.add(Map.of("x", x, "y", y));
        }

        return new FitModels.FitResult(equation.equation(), parameters, rSquared, rmse, points.size(), used,
                skipped == null ? List.of() : List.copyOf(skipped), curve, pointViews, comparisons);
    }

    private List<FitModels.DataPoint> filterForEquation(ExpressionParser.CompiledEquation equation, List<FitModels.DataPoint> points) {
        String rhs = equation.rhs() == null ? "" : equation.rhs().toLowerCase();
        boolean needsPositiveX = rhs.contains("log(") || rhs.contains("ln(") || rhs.contains("sqrt(");
        if (!needsPositiveX) return points;
        List<FitModels.DataPoint> filtered = new ArrayList<>();
        for (FitModels.DataPoint point : points) {
            if (point.x() > 0) filtered.add(point);
        }
        return filtered;
    }

    private double[] initialGuess(int n, List<FitModels.DataPoint> points) {
        double[] guess = new double[n];
        double meanY = points.stream().mapToDouble(FitModels.DataPoint::y).average().orElse(1);
        for (int i = 0; i < n; i++) guess[i] = i == 0 ? meanY : (i % 2 == 0 ? 0.1 : -0.1);
        return guess;
    }
}
