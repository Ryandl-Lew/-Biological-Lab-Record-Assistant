package com.bionote.agent.fit;

import com.bionote.agent.api.AgentDtos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartPlotServiceTest {
    private final ChartPlotService charts = new ChartPlotService(null);

    @Test
    void detectsChartRequests() {
        assertTrue(charts.looksLikeChartRequest("请画折线图，x=浓度，y=Ct"));
        assertTrue(charts.looksLikeChartRequest("生成柱状图"));
        assertTrue(charts.looksLikeChartRequest("plot a bar chart"));
        assertFalse(charts.looksLikeChartRequest("项目有多少成员"));
        assertEquals("bar", charts.detectChartType("画柱状图"));
        assertEquals("line", charts.detectChartType("画折线图"));
        assertEquals("scatter", charts.detectChartType("散点图"));
    }

    @Test
    void handlesAxisFollowUpAfterChartAsk() {
        assertTrue(charts.looksLikeAxisMapping("x轴时间，y轴残糖"));
        assertTrue(charts.shouldHandleChart(
                "x轴时间，y轴残糖",
                List.of(new AgentDtos.ChatMessage("user", "帮我按excel内容画折线图"),
                        new AgentDtos.ChatMessage("assistant", "该表格可用列：时间、残糖。请指定横轴与纵轴。"))));
        assertFalse(charts.shouldHandleChart("x轴时间，y轴残糖", List.of()));
    }

    @Test
    void parsesExplicitAxesFromCatalogColumnsOnly() {
        String catalog = "- EXP-1 | demo | COMPLETED | FERM | fields=[] | tables=[file=\"0302.xlsx\" columns=[时间, 发酵时长, PH, 温度, 残糖, OD, 备注]]\n";
        String[] axes = charts.parseExplicitAxes(
                "帮我按excel内容画折线图\nx轴时间，y轴残糖", catalog);
        assertEquals("时间", axes[0]);
        assertEquals("残糖", axes[1]);
    }

    @Test
    void parsesAxisWithShiAndParentheticalColumn() {
        String catalog = "- EXP-1 | demo | COMPLETED | FERM | fields=[] | tables=[file=\"0302.xlsx\" columns=[时间, 发酵时长, 耗碱量（显示值）, 残糖]]\n";
        String[] axes = charts.parseExplicitAxes(
                "x轴是发酵时长，y轴是耗碱量（显示值）", catalog);
        assertEquals("发酵时长", axes[0]);
        assertEquals("耗碱量（显示值）", axes[1]);
        String[] shortY = charts.parseExplicitAxes("x轴是发酵时长，y轴是耗碱量", catalog);
        assertEquals("发酵时长", shortY[0]);
        assertEquals("耗碱量（显示值）", shortY[1]);
    }

    @Test
    void ignoresInstructionalAxisProse() {
        String catalog = "- EXP-1 | demo | COMPLETED | FERM | fields=[] | tables=[file=\"0302.xlsx\" columns=[时间, 残糖, OD]]\n";
        String[] polluted = charts.parseExplicitAxes(
                "这是单变量折线/柱状图，不是多元回归。请只选一列横轴和一列纵轴。", catalog);
        assertNull(polluted[0]);
        assertNull(polluted[1]);
    }

    @Test
    void buildsChartFromFitView() {
        AgentDtos.FitView fit = new AgentDtos.FitView(
                "y=a+b*x",
                Map.of("a", 1.0, "b", 2.0),
                0.99,
                0.1,
                3,
                List.of("EXP-1"),
                List.of(),
                List.of(new AgentDtos.FitCurvePoint(1, 3), new AgentDtos.FitCurvePoint(2, 5)),
                List.of(new AgentDtos.FitPointView(1, 3.1, "EXP-1", "CSV"),
                        new AgentDtos.FitPointView(2, 4.9, "EXP-1", "CSV")),
                List.of());
        AgentDtos.ChartView chart = charts.fromFit(fit);
        assertNotNull(chart);
        assertEquals("scatter", chart.type());
        assertEquals(2, chart.series().size());
        assertEquals(2, chart.series().get(0).points().size());
    }
}
