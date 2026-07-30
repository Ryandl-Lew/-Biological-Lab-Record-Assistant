package com.bionote.agent.tool.bionote;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface BioNoteAgentQueryStore {
    PageSlice listRecords(
            UUID projectId, Instant start, Instant end, List<String> statuses, int page, int size);

    long revisionCount(UUID recordId, int maxRevisionNo);

    long activeAttachmentCount(UUID recordId);

    List<Map<String, Object>> listAttachments(UUID recordId);

    List<Map<String, Object>> listProjectAttachments(UUID projectId);

    Optional<Map<String, Object>> findAttachment(UUID attachmentId, UUID recordId);

    Optional<Map<String, Object>> findAnyAttachment(UUID attachmentId);

    List<Map<String, Object>> listRevisions(
            UUID recordId, int maxRevisionNo, Instant start, Instant end);

    List<Map<String, Object>> listReviews(UUID recordId, int maxRevisionNo);

    PageSlice activity(
            UUID projectId,
            Instant start,
            Instant end,
            List<String> eventTypes,
            int page,
            int size);

    Optional<Map<String, Object>> latestProjectReport(UUID projectId, UUID excludedRunId);

    Optional<Map<String, Object>> findRecord(UUID recordId, UUID projectId);

    Optional<UUID> findRevisionRecord(
            UUID revisionId, UUID projectId, int maxRevisionNo, Instant through);

    String inputCursorJson(UUID runId);

    Optional<Map<String, Object>> findReview(UUID reviewId);

    record PageSlice(List<Map<String, Object>> items, long total) {}
}
