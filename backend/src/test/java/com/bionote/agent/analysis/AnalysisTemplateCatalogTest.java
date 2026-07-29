package com.bionote.agent.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisTemplateCatalogTest {
    @Test
    void resolvesYsm01ToGeneralProcessKineticsTemplate() {
        var template = AnalysisTemplateCatalog.resolveFromMessage("请按 YSM01 混合动力学模型做分析报告");
        assertNotNull(template);
        assertEquals("process_kinetics_proxy", template.id());
        assertTrue(template.outputSections().stream().anyMatch(s -> s.contains("分析边界")));
    }

    @Test
    void resolvesDoseResponseTemplate() {
        var template = AnalysisTemplateCatalog.resolveFromMessage("对这份标曲做剂量反应叙述分析");
        assertNotNull(template);
        assertEquals("dose_response_narrative", template.id());
    }

    @Test
    void resolvesExperimentSummaryForWesternBlot() {
        var template = AnalysisTemplateCatalog.resolveFromMessage("帮我总结这个Western Blot实验");
        assertNotNull(template);
        assertEquals("experiment_summary", template.id());
        assertTrue(template.outputSections().contains("实验目的"));
        assertTrue(template.outputSections().contains("实验结果与观察"));
    }

    @Test
    void resolvesExperimentSummaryForGenericAnalysis() {
        var template = AnalysisTemplateCatalog.resolveFromMessage("分析这个实验记录");
        assertNotNull(template);
        assertEquals("experiment_summary", template.id());
    }

    @Test
    void resolvesProjectStatusReport() {
        var template = AnalysisTemplateCatalog.resolveFromMessage("告诉我这个项目目前进展如何");
        assertNotNull(template);
        assertEquals("project_status_report", template.id());
        assertTrue(template.outputSections().contains("已完成实验"));
        assertTrue(template.outputSections().contains("下一步建议"));
    }

    @Test
    void resolvesCsvDataAnalysis() {
        var template = AnalysisTemplateCatalog.resolveFromMessage("帮我分析这个CSV数据");
        assertNotNull(template);
        assertEquals("csv_data_analysis", template.id());
        assertTrue(template.suggestedFits().size() >= 1);
    }

    @Test
    void allTemplatesHaveRequiredFields() {
        for (var template : AnalysisTemplateCatalog.all()) {
            assertNotNull(template.id(), "id required for " + template.label());
            assertNotNull(template.label(), "label required for " + template.id());
            assertFalse(template.outputSections().isEmpty(), "outputSections required for " + template.id());
            assertNotNull(template.systemAddendum(), "systemAddendum required for " + template.id());
        }
    }

    @Test
    void catalogDescriptionMentionsAllTemplates() {
        String desc = AnalysisTemplateCatalog.catalogDescription();
        for (var template : AnalysisTemplateCatalog.all()) {
            assertTrue(desc.contains(template.id()), "catalog should mention " + template.id());
        }
    }
}
