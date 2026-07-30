package com.bionote.agent.report;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ProjectProgressStore {
    Optional<String> findRole(UUID projectId, UUID actorId);

    Optional<Map<String, Object>> findProjectOverview(UUID projectId);

    Map<String, Long> memberCounts(UUID projectId);

    Map<String, Long> recordCounts(UUID projectId);

    Map<String, Object> activityBounds(UUID projectId, Instant through);

    Map<String, Long> eventCounts(UUID projectId, Instant start, Instant end);

    List<Map<String, Object>> blockingRecords(UUID projectId, Instant staleBefore);
}
