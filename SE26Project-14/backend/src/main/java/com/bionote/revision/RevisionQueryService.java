package com.bionote.revision;

import com.bionote.common.PagedResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RevisionQueryService implements RevisionQueryUseCase {
    private final RevisionStore repository;

    public RevisionQueryService(RevisionStore repository) {
        this.repository = repository;
    }

    public PagedResponse<RevisionDtos.RevisionSummary> list(
            UUID actorId, UUID recordId, int page, int size) {
        return repository.list(actorId, recordId, page, size);
    }

    public RevisionDtos.RevisionDetail detail(UUID actorId, UUID recordId, UUID revisionId) {
        return repository.detail(actorId, recordId, revisionId);
    }

    public RevisionDtos.RevisionDetail legacyDetail(UUID actorId, UUID revisionId) {
        return repository.detail(actorId, null, revisionId);
    }
}
