package com.bionote.dashboard;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DashboardService implements DashboardUseCase {
    private final DashboardQueryStore queries;

    public DashboardService(DashboardQueryStore queries) {
        this.queries = queries;
    }

    @Override
    public List<DashboardDtos.Task> tasks(UUID userId) {
        return queries.tasks(userId);
    }

    @Override
    public DashboardDtos.Summary summary(UUID userId) {
        return queries.summary(userId);
    }
}
