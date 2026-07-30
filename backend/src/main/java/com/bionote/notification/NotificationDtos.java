package com.bionote.notification;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class NotificationDtos {
    private NotificationDtos() {}

    public record View(
            UUID id,
            String type,
            String title,
            String body,
            Map<String, Object> target,
            List<String> actions,
            boolean stale,
            Instant createdAt,
            Instant readAt) {}
}
