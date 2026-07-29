package com.bionote.agent.fit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FitIntentParserTest {
    private final FitIntentParser parser = new FitIntentParser(new ObjectMapper());

    @Test
    void parsesChineseFitRequestWithoutStealingYEquals() {
        FitModels.FitIntent intent = parser.parse(
                "拟合 方程：y = a + b*x ；xField=concentration ；yField=ct ；状态 COMPLETED");
        assertThat(intent.fitRequested()).isTrue();
        assertThat(intent.equation()).containsIgnoringCase("y");
        assertThat(intent.equation()).contains("a + b*x");
        assertThat(intent.xSpec()).isEqualTo("concentration");
        assertThat(intent.ySpec()).isEqualTo("ct");
        assertThat(intent.statuses()).contains("COMPLETED");
        assertThat(intent.missingPrompt()).isNull();
    }

    @Test
    void asksForMissingMapping() {
        FitModels.FitIntent intent = parser.parse("请拟合 y=a*exp(-k*x)+b");
        assertThat(intent.fitRequested()).isTrue();
        assertThat(intent.missingPrompt()).contains("自变量");
    }

    @Test
    void parsesJsonBlock() {
        FitModels.FitIntent intent = parser.parse("""
                帮我算一下 {"equation":"y=a+b*x","xField":"x","yField":"y","recordCodes":["EXP-20260727-ABC"],"statuses":["COMPLETED"]}
                """);
        assertThat(intent.fitRequested()).isTrue();
        assertThat(intent.xSpec()).isEqualTo("x");
        assertThat(intent.ySpec()).isEqualTo("y");
        assertThat(intent.recordCodes()).containsExactly("EXP-20260727-ABC");
    }
}
