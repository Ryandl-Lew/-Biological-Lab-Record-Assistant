package com.bionote.restore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bionote.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RestorePreviewTokenServiceTest {
    private static final String SECRET = "restore-test-secret-with-at-least-32-chars";

    @Test
    void tokenBindsClaimsAndRejectsTampering() {
        Instant now = Instant.parse("2026-07-26T10:00:00Z");
        var service =
                new RestorePreviewTokenService(
                        new ObjectMapper().findAndRegisterModules(),
                        SECRET,
                        Clock.fixed(now, ZoneOffset.UTC));
        UUID actor = UUID.randomUUID(),
                record = UUID.randomUUID(),
                project = UUID.randomUUID(),
                revision = UUID.randomUUID();
        var issued =
                service.issue(
                        actor, record, project, revision, 7, true, "a".repeat(64), "b".repeat(64));
        assertThat(service.verify(issued.token()).actorId()).isEqualTo(actor);
        assertThat(issued.expiresAt()).isEqualTo(now.plusSeconds(300));
        String tampered =
                (issued.token().charAt(0) == 'A' ? "B" : "A") + issued.token().substring(1);
        assertThatThrownBy(() -> service.verify(tampered))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo("RESTORE_PREVIEW_STALE");
    }

    @Test
    void expiredTokenIsRejected() {
        Instant now = Instant.parse("2026-07-26T10:00:00Z");
        var issuer =
                new RestorePreviewTokenService(
                        new ObjectMapper().findAndRegisterModules(),
                        SECRET,
                        Clock.fixed(now, ZoneOffset.UTC));
        String token =
                issuer.issue(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                1,
                                false,
                                "a".repeat(64),
                                "b".repeat(64))
                        .token();
        var verifier =
                new RestorePreviewTokenService(
                        new ObjectMapper().findAndRegisterModules(),
                        SECRET,
                        Clock.fixed(now.plusSeconds(301), ZoneOffset.UTC));
        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo("RESTORE_PREVIEW_STALE");
    }
}
