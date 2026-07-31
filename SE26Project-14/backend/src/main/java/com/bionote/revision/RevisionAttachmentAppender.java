package com.bionote.revision;

import java.util.List;
import java.util.UUID;

/** Append-only persistence port for immutable revision attachment membership. */
public interface RevisionAttachmentAppender {
    void append(UUID revisionId, List<UUID> attachmentIds);
}
