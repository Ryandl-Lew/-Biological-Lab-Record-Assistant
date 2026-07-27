package com.bionote.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class UserDtos {
    private UserDtos() {}
    public record UserView(UUID id, String displayName, String email, String avatarUrl, Instant createdAt, Instant updatedAt) {}
    public record UpdateProfileRequest(@NotBlank(message="显示名不能为空") @Size(max=50, message="显示名最多 50 个字符") String displayName) {}
}

