package com.bionote.restore;

import java.util.UUID;

public interface RestoreExecutionUseCase {
    RestoreDtos.Result restore(UUID actorId, UUID recordId, RestoreDtos.ExecuteRequest request, String idempotencyKey);
}
