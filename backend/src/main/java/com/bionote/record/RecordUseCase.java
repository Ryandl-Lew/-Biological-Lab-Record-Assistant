package com.bionote.record;

import com.bionote.common.PagedResponse;
import java.util.UUID;

public interface RecordUseCase {
    RecordDtos.View create(UUID userId, RecordDtos.CreateRequest request);

    RecordDtos.View reserve(UUID userId, RecordDtos.ReserveRequest request);

    RecordDtos.View update(UUID userId, UUID recordId, RecordDtos.UpdateRequest request);

    void discardReservation(UUID userId, UUID recordId);

    PagedResponse<RecordDtos.View> list(
            UUID userId,
            UUID projectId,
            UUID creatorId,
            String status,
            String keyword,
            int page,
            int size);

    RecordDtos.View get(UUID userId, UUID recordId);

    void delete(UUID userId, UUID recordId);
}
