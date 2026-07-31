package com.bionote.agent.fit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.bionote.common.ApiException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CurveFitEngineTest {
    private final CurveFitEngine engine = new CurveFitEngine();

    @Test
    void fitsLinearModel() {
        var eq = ExpressionParser.parseEquation("y = a + b*x");
        UUID id = UUID.randomUUID();
        List<FitModels.DataPoint> points =
                List.of(
                        new FitModels.DataPoint(1, 3, id, "EXP-1", "TEMPLATE_FIELDS"),
                        new FitModels.DataPoint(2, 5, id, "EXP-2", "TEMPLATE_FIELDS"),
                        new FitModels.DataPoint(3, 7, id, "EXP-3", "TEMPLATE_FIELDS"),
                        new FitModels.DataPoint(4, 9, id, "EXP-4", "TEMPLATE_FIELDS"));
        FitModels.FitResult result = engine.fit(eq, points, List.of());
        assertThat(result.n()).isEqualTo(4);
        assertThat(result.parameters().get("a")).isCloseTo(1.0, within(1e-3));
        assertThat(result.parameters().get("b")).isCloseTo(2.0, within(1e-3));
        assertThat(result.rSquared()).isCloseTo(1.0, within(1e-6));
        assertThat(result.curveSample()).isNotEmpty();
        assertThat(result.comparisons()).isEmpty();
    }

    @Test
    void fitBestPrefersLinearOnLinearData() {
        UUID id = UUID.randomUUID();
        List<FitModels.DataPoint> points =
                List.of(
                        new FitModels.DataPoint(1, 3, id, "EXP-1", "TEMPLATE_FIELDS"),
                        new FitModels.DataPoint(2, 5, id, "EXP-2", "TEMPLATE_FIELDS"),
                        new FitModels.DataPoint(3, 7, id, "EXP-3", "TEMPLATE_FIELDS"),
                        new FitModels.DataPoint(4, 9, id, "EXP-4", "TEMPLATE_FIELDS"),
                        new FitModels.DataPoint(5, 11, id, "EXP-5", "TEMPLATE_FIELDS"));
        List<ExpressionParser.CompiledEquation> equations =
                FitMethodCatalog.allEquations().stream()
                        .map(ExpressionParser::parseEquation)
                        .toList();
        FitModels.FitResult best = engine.fitBest(equations, points, List.of());
        assertThat(best.equation()).contains("a+b*x");
        assertThat(best.comparisons()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(best.comparisons().stream().filter(FitModels.ModelComparison::selected).count())
                .isEqualTo(1);
        assertThat(best.rSquared()).isCloseTo(1.0, within(1e-4));
    }

    @Test
    void rejectsInsufficientPoints() {
        var eq = ExpressionParser.parseEquation("y = a + b*x");
        List<FitModels.DataPoint> points =
                List.of(
                        new FitModels.DataPoint(1, 1, UUID.randomUUID(), "A", "TEMPLATE_FIELDS"),
                        new FitModels.DataPoint(2, 2, UUID.randomUUID(), "B", "TEMPLATE_FIELDS"));
        assertThatThrownBy(() -> engine.fit(eq, points, List.of()))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo("FIT_INSUFFICIENT_POINTS");
    }
}
