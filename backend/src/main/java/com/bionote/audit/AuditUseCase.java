package com.bionote.audit;

import com.bionote.common.PagedResponse;

import java.time.LocalDate;
import java.util.UUID;

public interface AuditUseCase {
    PagedResponse<AuditDtos.View> list(UUID userId, UUID projectId, String eventType, UUID actorId, LocalDate from, LocalDate to, int page, int size);
    PagedResponse<AuditDtos.AttachmentSummary> attachments(UUID userId, UUID projectId, int page, int size);
}
