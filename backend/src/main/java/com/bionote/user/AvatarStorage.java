package com.bionote.user;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** Replaceable storage capability for user avatars. */
public interface AvatarStorage {
    StoredAvatar store(MultipartFile file);
    byte[] read(String key) throws IOException;
    void deleteQuietly(String key);

    record StoredAvatar(String key, String mime) {}
}
