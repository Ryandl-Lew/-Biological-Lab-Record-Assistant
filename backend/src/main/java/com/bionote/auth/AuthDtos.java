package com.bionote.auth;

import com.bionote.user.UserDtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}
    public record RegisterRequest(@NotBlank(message="显示名不能为空") @Size(max=50) String displayName,
                                  @NotBlank String email,
                                  @NotBlank @Size(min=8,max=72,message="密码长度须为 8–72 位") String password) {}
    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
    public record AuthResult(String accessToken, UserDtos.UserView user) {}
}
