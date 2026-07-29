package com.bionote.revision;

import com.bionote.common.PagedResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read-side persistence port for revision history, details and diff sources. */
public interface RevisionStore {
    PagedResponse<RevisionDtos.RevisionSummary> list(UUID actorId, UUID recordId, int page, int size);
    RevisionDtos.RevisionDetail detail(UUID actorId, UUID recordId, UUID revisionId);
    SourceRecord revisionSource(UUID actorId, UUID recordId, UUID revisionId);
    SourceRecord workingCopySource(UUID actorId, UUID recordId);

    record SourceRecord(RevisionDtos.SnapshotSourceRef source, int schemaVersion, Map<String, Object> snapshot,
                        List<RevisionDtos.AttachmentView> attachments, RevisionDtos.ReviewSummary review,
                        String submitNote, Long recordVersion) {}
}
