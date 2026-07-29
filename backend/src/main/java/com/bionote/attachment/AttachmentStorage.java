package com.bionote.attachment;

import org.springframework.web.multipart.MultipartFile;

public interface AttachmentStorage {
    StoredFile store(MultipartFile file);
    byte[] read(String key);
    boolean exists(String key);
    void deleteQuietly(String key);
    record StoredFile(String storageKey,String originalFilename,String mediaType,long sizeBytes,boolean previewable) {}
}
