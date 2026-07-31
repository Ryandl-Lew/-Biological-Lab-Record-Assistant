package com.bionote.restore;

import java.util.UUID;

public interface RestorePreviewUseCase {
    RestoreDtos.Preview preview(UUID actorId, UUID recordId, RestoreDtos.PreviewRequest request);
}
