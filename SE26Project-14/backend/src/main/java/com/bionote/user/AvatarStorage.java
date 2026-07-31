package com.bionote.user;

import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

/** Replaceable storage capability for user avatars. */
public interface AvatarStorage {
    StoredAvatar store(MultipartFile file);

    byte[] read(String key) throws IOException;

    void deleteQuietly(String key);

    record StoredAvatar(String key, String mime) {}
}
