package com.bionote.agent.fit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bionote.attachment.AttachmentStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PointExtractorCsvTest {
    private final PointExtractor extractor =
            new PointExtractor(
                    mock(AgentFitStore.class), new ObjectMapper(), mock(AttachmentStorage.class));

    @Test
    void parsesCsvWithHeaderNames() {
        String csv = "concentration,ct\n1,10\n2,20\n3,30\n";
        List<FitModels.DataPoint> points =
                extractor.parseCsv(csv, UUID.randomUUID(), "EXP-1", "concentration", "ct");
        assertThat(points).hasSize(3);
        assertThat(points.get(1).x()).isEqualTo(2.0);
        assertThat(points.get(1).y()).isEqualTo(20.0);
        assertThat(points.get(0).source()).isEqualTo("CSV_ATTACHMENT");
    }

    @Test
    void parsesCsvByColumnIndex() {
        String csv = "0.5,1.5\n1.0,2.0\n1.5,2.5\n";
        List<FitModels.DataPoint> points =
                extractor.parseCsv(csv, UUID.randomUUID(), "EXP-2", "0", "1");
        assertThat(points).hasSize(3);
        assertThat(points.get(0).x()).isEqualTo(0.5);
        assertThat(points.get(0).y()).isEqualTo(1.5);
    }

    @Test
    void parsesSemicolonCsvAndSupportsPerfectLinearFit() {
        String csv = "time;value\n0;1\n1;3\n2;5\n3;7\n";
        List<FitModels.DataPoint> points =
                extractor.parseCsv(csv, UUID.randomUUID(), "EXP-3", "time", "value");
        assertThat(points).extracting(FitModels.DataPoint::x).containsExactly(0.0, 1.0, 2.0, 3.0);
        assertThat(points).extracting(FitModels.DataPoint::y).containsExactly(1.0, 3.0, 5.0, 7.0);
        FitModels.FitResult fit =
                new CurveFitEngine()
                        .fit(ExpressionParser.parseEquation("y=a+b*x"), points, List.of());
        assertThat(fit.rSquared()).isGreaterThan(0.999999);
    }
}
