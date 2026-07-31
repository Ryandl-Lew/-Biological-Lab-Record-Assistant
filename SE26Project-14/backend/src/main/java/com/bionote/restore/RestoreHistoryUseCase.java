package com.bionote.restore;

import com.bionote.common.PagedResponse;
import java.util.UUID;

public interface RestoreHistoryUseCase {
    PagedResponse<RestoreDtos.OperationSummary> list(
            UUID actorId, UUID recordId, int page, int size);
}
