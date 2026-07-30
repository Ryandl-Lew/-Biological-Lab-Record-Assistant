package com.bionote.revision;

import com.bionote.common.PagedResponse;
import java.util.UUID;

public interface RevisionQueryUseCase {
    PagedResponse<RevisionDtos.RevisionSummary> list(
            UUID actorId, UUID recordId, int page, int size);

    RevisionDtos.RevisionDetail detail(UUID actorId, UUID recordId, UUID revisionId);

    RevisionDtos.RevisionDetail legacyDetail(UUID actorId, UUID revisionId);
}
