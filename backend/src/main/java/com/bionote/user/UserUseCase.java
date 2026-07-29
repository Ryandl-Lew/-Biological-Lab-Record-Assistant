package com.bionote.user;

import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/** HTTP-facing user profile use cases. */
public interface UserUseCase {
    UserDtos.UserView update(UUID id, UserDtos.UpdateProfileRequest request);
    UserDtos.UserView avatar(UUID id, MultipartFile file);
    AvatarContent avatar(UUID userId);

    record AvatarContent(byte[] bytes, String mediaType) {}
}
