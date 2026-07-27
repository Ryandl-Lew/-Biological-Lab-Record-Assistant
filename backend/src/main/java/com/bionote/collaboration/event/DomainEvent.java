package com.bionote.collaboration.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public interface DomainEvent {
    UUID eventId();
    String eventType();
    UUID actorId();
    UUID projectId();
    UUID recordId();
    Instant occurredAt();
    Map<String, Object> metadata();
}
