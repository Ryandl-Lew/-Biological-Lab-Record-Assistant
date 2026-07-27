package com.bionote.record;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public final class RecordDtos {
    private RecordDtos() {}
    public record ReserveRequest(@NotNull UUID projectId, UUID templateId) {}
    public record CreateRequest(@NotNull UUID projectId,UUID templateId,@NotBlank @Size(max=255)String title,@NotBlank @Size(max=100)String experimentType,@NotNull LocalDate experimentDate,@NotBlank @Size(max=3000)String purpose){}
    public record UpdateRequest(@NotNull Long version,@NotBlank @Size(max=255)String title,@NotBlank @Size(max=100)String experimentType,@NotNull LocalDate experimentDate,@NotBlank @Size(max=3000)String purpose,Map<String,Object> fieldValues,Object contentJson,String contentHtml){}
    public record View(UUID id,String code,UUID projectId,String projectName,UUID creatorId,String creatorName,String title,String experimentType,LocalDate experimentDate,String purpose,String status,boolean provisional,Object templateSnapshot,Map<String,Object> fieldValues,Object contentJson,String contentHtml,String contentPlainText,int currentRevisionNo,UUID currentReviewId,Object displayedRevision,long version,Instant createdAt,Instant updatedAt,Map<String,Boolean> capabilities){}
}
