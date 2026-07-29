package com.bionote.export;

import java.util.UUID;

public interface ExportReportStore {
    LoadedReport loadCompleted(UUID userId,UUID recordId);
    record LoadedReport(UUID projectId,RecordReportModel report){}
}
