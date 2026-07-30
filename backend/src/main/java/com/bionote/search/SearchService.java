package com.bionote.search;

import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SearchService implements SearchUseCase {
    private final SearchQueryStore queries;

    public SearchService(SearchQueryStore queries) {
        this.queries = queries;
    }

    @Override
    public SearchDtos.Response search(
            UUID userId,
            String keyword,
            String entityType,
            UUID projectId,
            UUID creatorId,
            String recordStatus,
            int page,
            int size) {
        return queries.search(
                userId, keyword, entityType, projectId, creatorId, recordStatus, page, size);
    }
}
