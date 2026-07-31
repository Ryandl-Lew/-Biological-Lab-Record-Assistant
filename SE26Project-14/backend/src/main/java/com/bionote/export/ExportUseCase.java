package com.bionote.export;

import java.util.UUID;

public interface ExportUseCase {
    ExportDtos.Preview preview(UUID userId, UUID recordId);

    ExportDtos.FileExport markdown(UUID userId, UUID recordId);

    ExportDtos.FileExport pdf(UUID userId, UUID recordId);
}
