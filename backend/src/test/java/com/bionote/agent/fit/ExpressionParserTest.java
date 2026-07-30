package com.bionote.agent.fit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bionote.common.ApiException;
import org.junit.jupiter.api.Test;

class ExpressionParserTest {
    @Test
    void parsesLinearEquationAndEvaluates() {
        var eq = ExpressionParser.parseEquation("y = a + b*x");
        assertThat(eq.parameters()).containsExactly("a", "b");
        assertThat(eq.value(2, new double[] {1, 3})).isEqualTo(7.0);
    }

    @Test
    void parsesExpAndRejectsBadInput() {
        var eq = ExpressionParser.parseEquation("a*exp(-k*x)+b");
        assertThat(eq.parameters()).containsExactly("a", "k", "b");
        assertThat(eq.value(0, new double[] {2, 1, 0.5})).isEqualTo(2.5);

        assertThatThrownBy(() -> ExpressionParser.parseEquation("y = 1 + 2"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo("FIT_INVALID_EQUATION");
        assertThatThrownBy(() -> ExpressionParser.parseEquation("y = a + b*x; DROP TABLE"))
                .isInstanceOf(ApiException.class);
    }
}
