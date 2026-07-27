package com.bionote.user;

import com.bionote.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AvatarStorageService {
    private final Path root;
    private static final Set<String> MIME = Set.of("image/jpeg", "image/png", "image/webp");
    public record StoredAvatar(String key, String mime) {}

    public AvatarStorageService(@Value("${bionote.upload-root:./uploads}") String uploadRoot) throws IOException {
        root = Path.of(uploadRoot).toAbsolutePath().normalize().resolve("avatars");
        Files.createDirectories(root);
    }

    public StoredAvatar store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "头像文件不能为空");
        if (file.getSize() > 2 * 1024 * 1024) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "头像不能超过 2 MB");
        String mime = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String ext = name.endsWith(".jpg") || name.endsWith(".jpeg") ? ".jpg" : name.endsWith(".png") ? ".png" : name.endsWith(".webp") ? ".webp" : "";
        try {
            byte[] bytes = file.getBytes();
            if (!MIME.contains(mime) || ext.isEmpty() || !matchesSignature(bytes, mime))
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE", "只允许真实的 JPEG、PNG 或 WebP 图片");
            String key = UUID.randomUUID() + ext;
            Path target = root.resolve(key).normalize();
            if (!target.startsWith(root)) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE", "非法文件名");
            Files.write(target, bytes);
            return new StoredAvatar(key, mime);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR", "头像保存失败");
        }
    }

    private boolean matchesSignature(byte[] b, String mime) {
        if (mime.equals("image/jpeg")) return b.length > 3 && (b[0]&255)==0xFF && (b[1]&255)==0xD8 && (b[2]&255)==0xFF;
        if (mime.equals("image/png")) return b.length > 8 && (b[0]&255)==0x89 && b[1]==0x50 && b[2]==0x4E && b[3]==0x47;
        return b.length > 12 && b[0]=='R' && b[1]=='I' && b[2]=='F' && b[3]=='F' && b[8]=='W' && b[9]=='E' && b[10]=='B' && b[11]=='P';
    }

    public byte[] read(String key) throws IOException { return Files.readAllBytes(root.resolve(key).normalize()); }
    public void deleteQuietly(String key) { if (key != null) try { Files.deleteIfExists(root.resolve(key).normalize()); } catch (IOException ignored) {} }
}

