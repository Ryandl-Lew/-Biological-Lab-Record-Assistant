package com.bionote.agent.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface AgentChatContextStore {
    List<Map<String,Object>> recentRevisions(UUID recordId);
    List<Map<String,Object>> recentReviews(UUID recordId);
    List<Map<String,Object>> activeAttachments(UUID recordId);
    List<Map<String,Object>> latestRecordArtifact(UUID recordId);
    long memberCount(UUID projectId);
    List<Map<String,Object>> memberRoleCounts(UUID projectId);
    List<Map<String,Object>> recordStatusCounts(UUID projectId);
    List<Map<String,Object>> recentRecords(UUID projectId);
    List<Map<String,Object>> latestProjectArtifact(UUID projectId);
    Optional<Map<String,Object>> findVisibleRecord(UUID actorId,UUID recordId);
    Optional<Map<String,Object>> findVisibleProject(UUID actorId,UUID projectId);
}
