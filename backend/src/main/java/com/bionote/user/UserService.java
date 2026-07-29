package com.bionote.user;

import com.bionote.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public class UserService implements UserUseCase {
    private final UserRepository users;
    private final AvatarStorage avatars;
    public UserService(UserRepository users, AvatarStorage avatars) { this.users=users; this.avatars=avatars; }

    public User require(UUID id) { return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "用户不存在")); }
    public UserDtos.UserView view(User u) {
        return new UserDtos.UserView(u.getId(), u.getDisplayName(), u.getEmailNormalized(),
                u.getAvatarStorageKey() == null ? null : "/api/v1/users/"+u.getId()+"/avatar", u.getCreatedAt(), u.getUpdatedAt());
    }

    @Override @Transactional public UserDtos.UserView update(UUID id, UserDtos.UpdateProfileRequest request) {
        User user = require(id); user.setDisplayName(request.displayName().trim()); return view(users.save(user));
    }

    @Override @Transactional public UserDtos.UserView avatar(UUID id, MultipartFile file) {
        User user = require(id); String old = user.getAvatarStorageKey();
        var stored = avatars.store(file); user.setAvatarStorageKey(stored.key()); user.setAvatarMimeType(stored.mime());
        users.save(user); avatars.deleteQuietly(old); return view(user);
    }

    @Override public AvatarContent avatar(UUID userId) {
        User user = require(userId);
        if (user.getAvatarStorageKey() == null) return null;
        try {
            return new AvatarContent(avatars.read(user.getAvatarStorageKey()), user.getAvatarMimeType());
        } catch (java.io.IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR", "头像读取失败");
        }
    }
}

