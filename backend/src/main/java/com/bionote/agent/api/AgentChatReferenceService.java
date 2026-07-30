package com.bionote.agent.api;

import com.bionote.agent.fit.PointExtractor;
import com.bionote.attachment.AttachmentStorage;
import com.bionote.common.ApiException;
import com.bionote.project.ProjectMemberStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Project-scoped temporary files for agent chat reference.
 * Does not write into experiment record attachments / revisions.
 */
@Service
public class AgentChatReferenceService implements AgentChatReferenceUseCase {
    private static final int MAX_TEXT_PREVIEW = 12_000;
    private static final int MAX_REFS_PER_REQUEST = 5;
    private static final long TTL_HOURS = 24;

    private final AgentChatReferenceStore references;
    private final ProjectMemberStore members;
    private final AttachmentStorage storage;
    private final PointExtractor extractor;
    private final ObjectMapper json;

    public AgentChatReferenceService(AgentChatReferenceStore references, ProjectMemberStore members,
                                     AttachmentStorage storage, PointExtractor extractor, ObjectMapper json) {
        this.references = references;
        this.members = members;
        this.storage = storage;
        this.extractor = extractor;
        this.json = json;
    }

    @Transactional
    public AgentDtos.ChatReferenceView upload(UUID actor, UUID projectId, MultipartFile file) {
        requireMember(actor, projectId);
        AttachmentStorage.StoredFile stored = storage.store(file);
        byte[] bytes = storage.read(stored.storageKey());
        Map<String, Object> peek = buildPeek(stored.originalFilename(), stored.mediaType(), bytes);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Instant expires = now.plus(TTL_HOURS, ChronoUnit.HOURS);
        try {
            references.insert(new AgentChatReferenceStore.ChatReference(id,projectId,actor,stored.storageKey(),
                    stored.originalFilename(),stored.mediaType(),stored.sizeBytes(),json.writeValueAsString(peek),now,expires));
        } catch (Exception e) {
            storage.deleteQuietly(stored.storageKey());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AGENT_REF_SAVE_FAILED", "参考文件元数据保存失败");
        }
        return toView(id, stored.originalFilename(), stored.mediaType(), stored.sizeBytes(), peek, expires);
    }

    @Transactional
    public void delete(UUID actor, UUID projectId, UUID referenceId) {
        requireMember(actor, projectId);
        List<Map<String,Object>> rows=references.findOwnedActive(referenceId,projectId,actor,Instant.now())
                .map(value -> List.<Map<String,Object>>of(Map.of("storage_key",value.storageKey())))
                .orElseGet(List::of);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "参考文件不存在或已过期");
        }
        String key = String.valueOf(rows.get(0).get("storage_key"));
        references.delete(referenceId);
        storage.deleteQuietly(key);
    }

    /** Read the raw bytes and filename of a chat reference for tool processing (plot/fit). */
    public ChatReferenceFile readReferenceFile(UUID actor, UUID projectId, UUID referenceId) {
        requireMember(actor, projectId);
        AgentChatReferenceStore.ChatReference ref = references.findActive(referenceId, projectId, Instant.now())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "参考文件不存在或已过期"));
        byte[] bytes = storage.read(ref.storageKey());
        return new ChatReferenceFile(ref.originalFilename(), ref.contentType(), bytes);
    }

    public record ChatReferenceFile(String filename, String contentType, byte[] bytes) {}

    public String formatForContext(UUID actor, UUID projectId, List<UUID> referenceIds) {
        if (referenceIds == null || referenceIds.isEmpty()) return "";
        if (referenceIds.size() > MAX_REFS_PER_REQUEST) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AGENT_REF_LIMIT",
                    "单次对话最多关联 " + MAX_REFS_PER_REQUEST + " 个参考文件");
        }
        requireMember(actor, projectId);
        StringBuilder text = new StringBuilder();
        text.append("CHAT_FILE_REFERENCES:\n");
        text.append("These are temporary chat uploads (not experiment-record attachments). Read the content below directly.\n\n");
        int used = 0;
        for (UUID referenceId : referenceIds) {
            if (referenceId == null) continue;
            List<Map<String,Object>> rows=references.findActive(referenceId,projectId,Instant.now())
                    .map(value -> List.<Map<String,Object>>of(Map.of(
                            "original_filename",value.originalFilename(),"content_type",value.contentType(),
                            "size_bytes",value.sizeBytes(),"peek_json",value.peekJson())))
                    .orElseGet(List::of);
            if (rows.isEmpty()) {
                text.append("- missingOrExpired id=").append(referenceId).append('\n');
                continue;
            }
            Map<String, Object> row = rows.get(0);
            used++;
            String filename = String.valueOf(row.get("original_filename"));
            String contentType = String.valueOf(row.get("content_type"));
            text.append("--- BEGIN FILE: ").append(filename).append(" (")
                    .append(contentType).append(", ")
                    .append(row.get("size_bytes")).append(" bytes, ")
                    .append("referenceId=").append(referenceId)
                    .append(") ---\n");
            // Parse peek JSON and output clean content
            try {
                Map<?,?> peek = json.readValue(String.valueOf(row.get("peek_json")), Map.class);
                String textPreview = peek.get("textPreview") != null ? String.valueOf(peek.get("textPreview")) : null;
                if (textPreview != null && !textPreview.isBlank()) {
                    text.append(textPreview);
                    if (!textPreview.endsWith("\n")) text.append('\n');
                } else {
                    @SuppressWarnings("unchecked")
                    List<String> columns = peek.get("columns") instanceof List ? (List<String>) peek.get("columns") : List.of();
                    String note = peek.get("note") != null ? String.valueOf(peek.get("note")) : null;
                    if (!columns.isEmpty()) {
                        text.append("Columns: ").append(String.join(", ", columns)).append('\n');
                    }
                    if (note != null) {
                        text.append("Note: ").append(note).append('\n');
                    }
                    if (textPreview == null && columns.isEmpty()) {
                        text.append("(This file type cannot be read as text. Refer to the filename and user's description.)\n");
                    }
                }
            } catch (Exception e) {
                text.append("(Could not parse file preview)\n");
            }
            text.append("--- END FILE: ").append(filename).append(" ---\n\n");
        }
        if (used == 0) return "";
        return text.toString();
    }

    private Map<String, Object> buildPeek(String filename, String contentType, byte[] bytes) {
        Map<String, Object> peek = new LinkedHashMap<>();
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv") || lower.endsWith(".xlsx")) {
            List<String> headers = extractor.peekHeaders(bytes, filename);
            peek.put("kind", "table");
            peek.put("columns", headers);
            if (lower.endsWith(".csv")) {
                String sample = new String(bytes, 0, Math.min(bytes.length, MAX_TEXT_PREVIEW), StandardCharsets.UTF_8);
                peek.put("textPreview", sample);
            } else {
                // Extract data rows from Excel, up to 500 rows
                try {
                    List<String[]> rows = extractor.readXlsxRows(bytes);
                    StringBuilder sb = new StringBuilder();
                    if (!headers.isEmpty()) {
                        sb.append(String.join(",", headers)).append('\n');
                    }
                    int dataStart = headers.isEmpty() || !rows.isEmpty() && rows.get(0).length > 0 ? 
                            (headers.isEmpty() ? -1 : 0) : 0;
                    int rowCount = 0;
                    for (int i = Math.max(0, dataStart); i < rows.size() && rowCount < 500; i++) {
                        String[] row = rows.get(i);
                        if (row.length == 0) continue;
                        sb.append(String.join(",", row)).append('\n');
                        rowCount++;
                    }
                    peek.put("textPreview", sb.toString());
                } catch (Exception e) {
                    peek.put("note", "Excel headers extracted but data rows could not be read.");
                }
            }
        } else if (lower.endsWith(".txt") || lower.endsWith(".md")
                || (contentType != null && contentType.startsWith("text/"))) {
            peek.put("kind", "text");
            peek.put("textPreview", new String(bytes, 0, Math.min(bytes.length, MAX_TEXT_PREVIEW), StandardCharsets.UTF_8));
        } else {
            peek.put("kind", "opaque");
            peek.put("note", "Binary/office file uploaded; content not fully extractable for chat. Use filename and user description.");
        }
        return peek;
    }

    private AgentDtos.ChatReferenceView toView(UUID id, String filename, String contentType, long size,
                                               Map<String, Object> peek, Instant expiresAt) {
        @SuppressWarnings("unchecked")
        List<String> columns = peek.get("columns") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        String preview = peek.get("textPreview") == null ? null : String.valueOf(peek.get("textPreview"));
        String note = peek.get("note") == null ? null : String.valueOf(peek.get("note"));
        String kind = peek.get("kind") == null ? "opaque" : String.valueOf(peek.get("kind"));
        return new AgentDtos.ChatReferenceView(id, filename, contentType, size, kind, columns, preview, note, expiresAt);
    }

    private void requireMember(UUID actor, UUID projectId) {
        if (members.findRole(projectId,actor).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource not found or inaccessible");
        }
    }
}
