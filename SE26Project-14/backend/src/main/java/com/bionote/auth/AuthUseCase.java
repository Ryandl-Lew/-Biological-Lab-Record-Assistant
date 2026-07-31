package com.bionote.auth;

import com.bionote.user.UserDtos;
import java.util.UUID;

/** Authentication and current-session application boundary. */
public interface AuthUseCase {
    AuthDtos.AuthResult register(AuthDtos.RegisterRequest request);

    AuthDtos.AuthResult login(AuthDtos.LoginRequest request);

    UserDtos.UserView currentUser(UUID userId);
}
