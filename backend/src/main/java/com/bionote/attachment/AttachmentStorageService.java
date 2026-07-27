package com.bionote.attachment;

import com.bionote.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Service
public class AttachmentStorageService {
    private static final Map<String, Set<String>> TYPES = Map.of(
            "jpg", Set.of("image/jpeg"), "jpeg", Set.of("image/jpeg"), "png", Set.of("image/png"),
            "webp", Set.of("image/webp"), "pdf", Set.of("application/pdf"),
            "txt", Set.of("text/plain"), "md", Set.of("text/markdown", "text/plain"),
            "csv", Set.of("text/csv", "application/csv", "text/plain"),
            "xlsx", Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/zip"),
            "docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/zip")
    );
    private final Path root;
    private final long maxBytes;

    public AttachmentStorageService(@Value("${bionote.upload-root:./uploads}") String root,
                                    @Value("${bionote.upload-max-bytes:20971520}") long maxBytes) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
    }

    public StoredFile store(MultipartFile file) {
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").trim();
        if (file.isEmpty() || file.getSize() <= 0) fail("附件不能为空");
        if (file.getSize() > maxBytes) fail("附件不能超过 20 MB");
        if (name.isBlank() || name.length() > 255 || name.contains("/") || name.contains("\\") || !Paths.get(name).getFileName().toString().equals(name)) fail("文件名不合法");
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1 || name.substring(0, dot).contains(".")) fail("不允许双扩展名文件");
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        Set<String> allowed = TYPES.get(ext);
        String declared = Optional.ofNullable(file.getContentType()).orElse("").toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
        if (allowed == null || "svg".equals(ext) || !allowed.contains(declared)) fail("不支持的文件类型或 MIME 不匹配");
        byte[] bytes;
        try { bytes = file.getBytes(); } catch (IOException e) { throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_READ_FAILED", "无法读取附件"); }
        if (!signatureMatches(ext, bytes)) fail("文件内容与扩展名不匹配");
        String key = UUID.randomUUID().toString();
        Path path = resolve(key);
        try {
            Files.createDirectories(root);
            Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORAGE_FAILED", "附件保存失败");
        }
        return new StoredFile(key, name, canonicalType(ext), bytes.length, Set.of("jpg", "jpeg", "png", "webp", "pdf", "md").contains(ext));
    }

    public byte[] read(String key) {
        try { return Files.readAllBytes(resolve(key)); }
        catch (NoSuchFileException e) { throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "附件文件不存在"); }
        catch (IOException e) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_READ_FAILED", "附件读取失败"); }
    }

    public boolean exists(String key) { return Files.isRegularFile(resolve(key)); }

    public void deleteQuietly(String key) { try { Files.deleteIfExists(resolve(key)); } catch (IOException ignored) {} }

    private Path resolve(String key) {
        if (!key.matches("[0-9a-fA-F-]{36}")) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STORAGE_KEY", "附件存储键不合法");
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STORAGE_PATH", "附件路径不合法");
        return path;
    }

    private boolean signatureMatches(String ext, byte[] b) {
        if (Set.of("txt", "md", "csv").contains(ext)) {
            for (int i = 0; i < Math.min(b.length, 4096); i++) if (b[i] == 0) return false;
            return true;
        }
        if (b.length < 4) return false;
        return switch (ext) {
            case "jpg", "jpeg" -> (b[0]&255)==0xff && (b[1]&255)==0xd8 && (b[2]&255)==0xff;
            case "png" -> b.length >= 8 && (b[0]&255)==0x89 && b[1]==0x50 && b[2]==0x4e && b[3]==0x47 && b[4]==0x0d && b[5]==0x0a && b[6]==0x1a && b[7]==0x0a;
            case "webp" -> b.length >= 12 && ascii(b,0,4).equals("RIFF") && ascii(b,8,4).equals("WEBP");
            case "pdf" -> ascii(b,0,5).equals("%PDF-");
            case "xlsx", "docx" -> b[0]==0x50 && b[1]==0x4b && (b[2]==0x03 || b[2]==0x05 || b[2]==0x07) && (b[3]==0x04 || b[3]==0x06 || b[3]==0x08);
            default -> false;
        };
    }
    private String ascii(byte[] b,int start,int len){ if(b.length<start+len)return "";return new String(b,start,len,java.nio.charset.StandardCharsets.US_ASCII); }
    private String canonicalType(String ext){return switch(ext){case "jpg","jpeg"->"image/jpeg";case "png"->"image/png";case "webp"->"image/webp";case "pdf"->"application/pdf";case "txt"->"text/plain";case "md"->"text/markdown";case "csv"->"text/csv";case "xlsx"->"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";default->"application/vnd.openxmlformats-officedocument.wordprocessingml.document";};}
    private void fail(String message){throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ATTACHMENT",message);}
    public record StoredFile(String storageKey,String originalFilename,String mediaType,long sizeBytes,boolean previewable){}
}
