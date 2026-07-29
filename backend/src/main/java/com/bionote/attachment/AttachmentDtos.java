package com.bionote.attachment;

import java.time.Instant;
import java.util.UUID;

public final class AttachmentDtos {
    private AttachmentDtos() {}
    public record View(UUID id, UUID recordId, String originalFilename, String mediaType, long sizeBytes,
                       boolean previewable, UUID uploaderId, String uploaderName, Instant createdAt,
                       boolean deleted, boolean canDelete) {}
}
