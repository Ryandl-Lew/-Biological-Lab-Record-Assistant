package com.bionote.export;

public final class ExportDtos {
    private ExportDtos() {}

    public record Preview(String html, RecordReportModel report) {}

    public record FileExport(byte[] bytes, String filename, String mediaType) {}
}
