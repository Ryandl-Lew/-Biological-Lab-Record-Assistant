package com.bionote.agent.fit;

import com.bionote.agent.config.AgentProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FitProposalResolverMultiColumnTest {
    @Test
    void prefersLatestYAndXOverOlderResidualSugarHistory() {
        AgentProperties props = new AgentProperties();
        props.setProvider("fake");
        FitProposalResolver resolver = new FitProposalResolver(props, new ObjectMapper(), new FitIntentParser(new ObjectMapper()));
        String catalog = "file=\"新建 Microsoft Excel 工作簿.xlsx\" columns=[时间, 发酵长度, PH, 温度, 搅拌, 溶氧, 通风量, 耗碱量（显示值）, 补糖量, 残糖, OD, 备注]";
        String message = """
                因变量是残糖和od，其他的除了备注都是自变量
                y是耗碱值，x包括时间, 发酵长度, PH, 温度, 搅拌, 溶氧, 通风量
                多元线性回归
                """;
        FitModels.FitProposal proposal = resolver.resolve(message, catalog, null);
        assertEquals("耗碱量（显示值）", proposal.ySpec());
        assertTrue(proposal.xSpec().contains("时间"));
        assertTrue(proposal.xSpec().contains("通风量"));
        assertTrue(!proposal.xSpec().contains("残糖"));
        assertTrue(proposal.multivariate());
        assertTrue(proposal.recordCodes() == null || proposal.recordCodes().isEmpty());
        assertTrue(proposal.statuses() == null || proposal.statuses().isEmpty());
    }
}
