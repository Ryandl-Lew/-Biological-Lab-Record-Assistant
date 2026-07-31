package com.bionote.agent.fit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FitMethodCatalogTest {
    @Test
    void mapsAliasesAndDetectsAutoCompare() {
        assertThat(FitMethodCatalog.resolveFromMessage("请做线性拟合")).isEqualTo("y=a+b*x");
        assertThat(FitMethodCatalog.resolveFromMessage("指数衰减剂量反应")).isEqualTo("y=a*exp(-k*x)+b");
        assertThat(FitMethodCatalog.resolveFromMessage("用米氏方程拟合")).isEqualTo("y=a*x/(b+x)");
        assertThat(FitMethodCatalog.resolveFromMessage("幂函数")).isEqualTo("y=a*x^b");
        assertThat(FitMethodCatalog.normalizeEquation("linear")).isEqualTo("y=a+b*x");
        assertThat(FitMethodCatalog.normalizeEquation("线性")).isEqualTo("y=a+b*x");
        assertThat(FitMethodCatalog.wantsAutoCompare("方程不太确定，试试看")).isTrue();
        assertThat(FitMethodCatalog.wantsAutoCompare("明确线性拟合")).isFalse();
        assertThat(FitMethodCatalog.allEquations().size()).isGreaterThanOrEqualTo(10);
    }

    @Test
    void extractsCustomEquation() {
        assertThat(FitMethodCatalog.resolveFromMessage("请用 y=a/(b+x)+c 对附件拟合"))
                .isEqualTo("y=a/(b+x)+c");
        assertThat(FitMethodCatalog.extractCustomEquation("自定义方程：y = a * exp(-k*x)"))
                .isEqualTo("y=a*exp(-k*x)");
    }
}
