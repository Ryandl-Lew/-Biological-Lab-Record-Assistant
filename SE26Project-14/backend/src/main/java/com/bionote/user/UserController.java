package com.bionote.user;

import com.bionote.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserUseCase service;

    public UserController(UserUseCase service) {
        this.service = service;
    }

    private UUID current(Authentication a) {
        return UUID.fromString(a.getName());
    }

    @PutMapping("/me")
    ApiResponse<UserDtos.UserView> update(
            Authentication auth, @Valid @RequestBody UserDtos.UpdateProfileRequest request) {
        return ApiResponse.of(service.update(current(auth), request));
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<UserDtos.UserView> avatar(
            Authentication auth, @RequestPart("file") MultipartFile file) {
        return ApiResponse.of(service.avatar(current(auth), file));
    }

    @GetMapping("/{userId}/avatar")
    ResponseEntity<byte[]> avatar(@PathVariable UUID userId) throws Exception {
        UserUseCase.AvatarContent avatar = service.avatar(userId);
        if (avatar == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(MediaType.parseMediaType(avatar.mediaType()))
                .body(avatar.bytes());
    }
}
