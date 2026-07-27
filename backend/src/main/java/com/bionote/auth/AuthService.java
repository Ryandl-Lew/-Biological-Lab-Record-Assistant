package com.bionote.auth;

import com.bionote.common.ApiException;
import com.bionote.config.JwtService;
import com.bionote.user.User;
import com.bionote.user.UserRepository;
import com.bionote.user.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRepository users; private final PasswordEncoder passwords; private final JwtService jwt; private final UserService userService;
    public AuthService(UserRepository users, PasswordEncoder passwords, JwtService jwt, UserService userService){
        this.users=users;this.passwords=passwords;this.jwt=jwt;this.userService=userService;
    }
    private String email(String value){
        String normalized=value.trim().toLowerCase(Locale.ROOT);
        if(!normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","请检查输入内容",Map.of("email","邮箱格式不正确"));
        return normalized;
    }

    @Transactional public AuthDtos.AuthResult register(AuthDtos.RegisterRequest request){
        String email=email(request.email());
        if(users.existsByEmailNormalized(email)) throw new ApiException(HttpStatus.CONFLICT,"DUPLICATE_RESOURCE","该邮箱已注册", Map.of("email","该邮箱已注册"));
        User user=new User(UUID.randomUUID(), request.displayName().trim(), email, passwords.encode(request.password()), Instant.now());
        users.saveAndFlush(user);
        return new AuthDtos.AuthResult(jwt.issue(user.getId()), userService.view(user));
    }
    public AuthDtos.AuthResult login(AuthDtos.LoginRequest request){
        User user=users.findByEmailNormalized(email(request.email())).orElse(null);
        if(user==null || !passwords.matches(request.password(),user.getPasswordHash()))
            throw new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_CREDENTIALS","邮箱或密码错误");
        return new AuthDtos.AuthResult(jwt.issue(user.getId()), userService.view(user));
    }
}
