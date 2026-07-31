package com.bionote.agent.fit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bionote.attachment.AttachmentStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class PointExtractorExcelTest {
    private final PointExtractor extractor =
            new PointExtractor(
                    mock(AgentFitStore.class), new ObjectMapper(), mock(AttachmentStorage.class));

    @Test
    void peeksHeadersFromFirstSheet() throws Exception {
        byte[] bytes = workbookBytes(true);
        assertThat(extractor.peekHeaders(bytes, "标曲.xlsx")).containsExactly("log10_copies", "ct");
    }

    @Test
    void parsesFirstSheetByHeaderNames() throws Exception {
        byte[] bytes = workbookBytes(true);
        List<FitModels.DataPoint> points =
                extractor.parseXlsx(bytes, UUID.randomUUID(), "EXP-X", "log10_copies", "ct");
        assertThat(points).hasSize(3);
        assertThat(points.get(0).x()).isEqualTo(1.0);
        assertThat(points.get(0).y()).isEqualTo(33.0);
        assertThat(points.get(0).source()).isEqualTo("EXCEL_ATTACHMENT");
    }

    @Test
    void parsesFirstSheetByColumnIndexWithoutHeader() throws Exception {
        byte[] bytes = workbookBytes(false);
        List<FitModels.DataPoint> points =
                extractor.parseXlsx(bytes, UUID.randomUUID(), "EXP-Y", "0", "1");
        assertThat(points).hasSize(3);
        assertThat(points.get(2).x()).isEqualTo(3.0);
        assertThat(points.get(2).y()).isEqualTo(26.0);
    }

    private byte[] workbookBytes(boolean withHeader) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("curve");
            workbook.createSheet("ignored");
            int rowIdx = 0;
            if (withHeader) {
                Row header = sheet.createRow(rowIdx++);
                header.createCell(0).setCellValue("log10_copies");
                header.createCell(1).setCellValue("ct");
            }
            double[][] values = {{1, 33}, {2, 30}, {3, 26}};
            for (double[] pair : values) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(pair[0]);
                row.createCell(1).setCellValue(pair[1]);
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
