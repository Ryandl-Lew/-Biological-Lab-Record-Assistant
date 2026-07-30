package com.bionote.dashboard;

import java.util.List;
import java.util.UUID;

public interface DashboardQueryStore {
    List<DashboardDtos.Task> tasks(UUID userId);

    DashboardDtos.Summary summary(UUID userId);
}
