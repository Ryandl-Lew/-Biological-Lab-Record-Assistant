package com.bionote.restore;

import com.bionote.common.ApiException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RestorePreviewTokenService {
    private final ObjectMapper json;
    private final byte[] secret;
    private final Clock clock;

    @Autowired
    public RestorePreviewTokenService(
            ObjectMapper objectMapper, @Value("${bionote.restore-preview-secret}") String secret) {
        this(objectMapper, secret, Clock.systemUTC());
    }

    RestorePreviewTokenService(ObjectMapper objectMapper, String secret, Clock clock) {
        if (secret == null || secret.length() < 32)
            throw new IllegalStateException(
                    "RESTORE_PREVIEW_SECRET must contain at least 32 characters");
        this.json = objectMapper.copy();
        this.json.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.json.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    public Issued issue(
            UUID actorId,
            UUID recordId,
            UUID projectId,
            UUID sourceRevisionId,
            long expectedRecordVersion,
            boolean restoreAttachments,
            String currentHash,
            String sourceHash) {
        Instant expiresAt = clock.instant().plus(5, ChronoUnit.MINUTES);
        Claims claims =
                new Claims(
                        actorId,
                        recordId,
                        projectId,
                        sourceRevisionId,
                        expectedRecordVersion,
                        restoreAttachments,
                        currentHash,
                        sourceHash,
                        expiresAt,
                        UUID.randomUUID());
        try {
            String payload = encode(json.writeValueAsBytes(claims));
            return new Issued(payload + "." + encode(sign(payload)), expiresAt);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot issue restore preview token", exception);
        }
    }

    public Claims verify(String token) {
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.", -1);
            if (parts.length != 2
                    || !MessageDigest.isEqual(
                            sign(parts[0]), Base64.getUrlDecoder().decode(parts[1]))) throw stale();
            Claims claims = json.readValue(Base64.getUrlDecoder().decode(parts[0]), Claims.class);
            if (!clock.instant().isBefore(claims.expiresAt())) throw stale();
            return claims;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw stale();
        }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot sign restore preview token", exception);
        }
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private ApiException stale() {
        return new ApiException(HttpStatus.CONFLICT, "RESTORE_PREVIEW_STALE", "恢复预览已过期或无效，请重新预览");
    }

    public record Claims(
            UUID actorId,
            UUID recordId,
            UUID projectId,
            UUID sourceRevisionId,
            long expectedRecordVersion,
            boolean restoreAttachments,
            String currentHash,
            String sourceHash,
            Instant expiresAt,
            UUID nonce) {}

    public record Issued(String token, Instant expiresAt) {}
}
