package com.bionote.attachment;

import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface AttachmentUseCase {
    List<AttachmentDtos.View> list(UUID userId, UUID recordId);

    AttachmentDtos.View upload(UUID userId, UUID recordId, MultipartFile file);

    void delete(UUID userId, UUID attachmentId);

    FilePayload load(UUID userId, UUID attachmentId, boolean preview);

    List<AttachmentDtos.View> revisionAttachments(UUID userId, UUID revisionId);

    record FilePayload(byte[] bytes, String filename, String mediaType) {}
}
