package com.bionote.export;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record RecordReportModel(String title,String code,String projectName,String experimentType,LocalDate experimentDate,
                                String creatorName,String purpose,List<Field> fields,String contentHtml,String contentText,
                                int revisionNo,String reviewerName,Instant reviewedAt,String reviewComment,List<Attachment> attachments) {
    public record Field(String label,String value){}
    public record Attachment(String filename,long sizeBytes,String uploaderName,Instant uploadedAt){}
}
