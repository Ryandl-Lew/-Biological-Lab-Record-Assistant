package com.bionote.auth;

import com.bionote.common.ApiResponse;
import com.bionote.user.UserDtos;
import com.bionote.user.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth; private final UserService users;
    public AuthController(AuthService auth, UserService users){this.auth=auth;this.users=users;}
    @PostMapping("/register") ApiResponse<AuthDtos.AuthResult> register(@Valid @RequestBody AuthDtos.RegisterRequest request){return ApiResponse.of(auth.register(request));}
    @PostMapping("/login") ApiResponse<AuthDtos.AuthResult> login(@Valid @RequestBody AuthDtos.LoginRequest request){return ApiResponse.of(auth.login(request));}
    @GetMapping("/me") ApiResponse<UserDtos.UserView> me(Authentication a){return ApiResponse.of(users.view(users.require(UUID.fromString(a.getName()))));}
    @PostMapping("/logout") ResponseEntity<Void> logout(){return ResponseEntity.noContent().build();}
}

