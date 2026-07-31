package com.bionote.search;

import java.util.UUID;

public interface SearchQueryStore {
    SearchDtos.Response search(
            UUID userId,
            String keyword,
            String entityType,
            UUID projectId,
            UUID creatorId,
            String recordStatus,
            int page,
            int size);
}
