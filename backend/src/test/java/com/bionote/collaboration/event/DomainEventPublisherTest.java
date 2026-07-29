package com.bionote.collaboration.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainEventPublisherTest {
    @Test void registryMatchesEachHandlerOnceAndDeduplicatesSameRegistration() {
        AtomicInteger calls = new AtomicInteger();
        DomainEventHandler<TestEvent> handler = handler(TestEvent.class, HandlerPhase.TRANSACTIONAL_REQUIRED, calls, false);
        InProcessDomainEventPublisher publisher = new InProcessDomainEventPublisher(List.of(handler, handler));
        publisher.publish(event(Map.of()));
        assertThat(calls).hasValue(1);
    }

    @Test void transactionalFailurePropagates() {
        DomainEventHandler<TestEvent> handler = handler(TestEvent.class, HandlerPhase.TRANSACTIONAL_REQUIRED, new AtomicInteger(), true);
        assertThatThrownBy(() -> new InProcessDomainEventPublisher(List.of(handler)).publish(event(Map.of())))
                .isInstanceOf(IllegalStateException.class).hasMessage("required failed");
    }

    @Test void bestEffortFailureDoesNotEscapePublisher() {
        DomainEventHandler<TestEvent> handler = handler(TestEvent.class, HandlerPhase.AFTER_COMMIT_BEST_EFFORT, new AtomicInteger(), true);
        new InProcessDomainEventPublisher(List.of(handler)).publish(event(Map.of()));
    }

    @Test void sanitizerDropsUnknownAndSensitiveMetadata() {
        DomainEvent event = event(Map.of(
                "revisionNo", 2,
                "changedSections", List.of("SCALAR", "RICH_TEXT"),
                "previewToken", "secret-token",
                "prompt", "hidden prompt",
                "storageKey", "private-path",
                "content", "full body"));
        Map<String, Object> sanitized = new DomainEventMetadataSanitizer().sanitize(event);
        assertThat(sanitized).containsEntry("revisionNo", 2).containsKey("changedSections");
        assertThat(sanitized).doesNotContainKeys("previewToken", "prompt", "storageKey", "content");
    }

    private DomainEventHandler<TestEvent> handler(Class<TestEvent> type, HandlerPhase phase, AtomicInteger calls, boolean fail) {
        return new DomainEventHandler<>() {
            @Override public Class<TestEvent> eventType() { return type; }
            @Override public HandlerPhase phase() { return phase; }
            @Override public void handle(TestEvent event) { calls.incrementAndGet(); if (fail) throw new IllegalStateException(phase == HandlerPhase.TRANSACTIONAL_REQUIRED ? "required failed" : "best effort failed"); }
        };
    }

    private TestEvent event(Map<String, Object> metadata) {
        return new TestEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now(), metadata);
    }

    private record TestEvent(UUID eventId, UUID actorId, UUID projectId, UUID recordId,
                             Instant occurredAt, Map<String, Object> metadata) implements DomainEvent {
        @Override public String eventType() { return "RECORD_REVISION_RESTORED"; }
    }
}
