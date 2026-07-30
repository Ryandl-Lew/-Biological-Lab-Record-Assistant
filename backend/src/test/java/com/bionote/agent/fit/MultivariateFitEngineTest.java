package com.bionote.agent.fit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MultivariateFitEngineTest {
    @Test
    void fitsSimplePlane() {
        MultivariateFitEngine engine = new MultivariateFitEngine();
        // y = 1 + 2*x1 + 3*x2
        double[][] x = {
            {1, 0},
            {0, 1},
            {1, 1},
            {2, 1},
            {1, 2},
            {3, 2}
        };
        double[] y = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            y[i] = 1 + 2 * x[i][0] + 3 * x[i][1];
        }
        FitModels.FitResult result =
                engine.fit(List.of("x1", "x2"), "y", x, y, List.of(), List.of("R1"));
        assertEquals(6, result.n());
        assertTrue(result.rSquared() > 0.999);
        assertEquals(1.0, result.parameters().get("a"), 1e-6);
        assertEquals(2.0, result.parameters().get("b_x1"), 1e-6);
        assertEquals(3.0, result.parameters().get("b_x2"), 1e-6);
    }

    @Test
    void dropsDuplicateAndConstantColumnsInsteadOfFailing() {
        MultivariateFitEngine engine = new MultivariateFitEngine();
        double[][] x = {
            {1, 1, 5, 0},
            {2, 2, 5, 1},
            {3, 3, 5, 0},
            {4, 4, 5, 1},
            {5, 5, 5, 0},
            {6, 6, 5, 1}
        };
        double[] y = {3, 5, 7, 9, 11, 13}; // ≈ 1 + 2*col0
        FitModels.FitResult result =
                engine.fit(
                        List.of("t", "t_dup", "const", "noise"),
                        "sugar",
                        x,
                        y,
                        List.of(),
                        List.of("R1"));
        assertTrue(result.rSquared() > 0.99);
        assertTrue(result.equation().contains("已剔除"));
        assertTrue(result.parameters().containsKey("b_t"));
    }
}
