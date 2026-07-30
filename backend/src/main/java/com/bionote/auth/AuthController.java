package com.bionote.auth;

import com.bionote.common.ApiResponse;
import com.bionote.user.UserDtos;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthUseCase auth;

    public AuthController(AuthUseCase auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    ApiResponse<AuthDtos.AuthResult> register(
            @Valid @RequestBody AuthDtos.RegisterRequest request) {
        return ApiResponse.of(auth.register(request));
    }

    @PostMapping("/login")
    ApiResponse<AuthDtos.AuthResult> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return ApiResponse.of(auth.login(request));
    }

    @GetMapping("/me")
    ApiResponse<UserDtos.UserView> me(Authentication a) {
        return ApiResponse.of(auth.currentUser(UUID.fromString(a.getName())));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }
}
