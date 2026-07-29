package com.bionote.collaboration.event;

import com.bionote.audit.AuditEventAppender;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AuditEventHandler implements DomainEventHandler<DomainEvent> {
    private final AuditEventAppender writer;
    private final DomainEventMetadataSanitizer sanitizer;

    public AuditEventHandler(AuditEventAppender writer, DomainEventMetadataSanitizer sanitizer) {
        this.writer = writer; this.sanitizer = sanitizer;
    }

    @Override public Class<DomainEvent> eventType() { return DomainEvent.class; }
    @Override public int order() { return 100; }

    @Override
    public void handle(DomainEvent event) {
        Target target = target(event);
        if (target == null) return;
        writer.appendOnce(event.eventId(), event.actorId(), event.projectId(), event.recordId(), event.eventType(),
                target.type(), target.id(), sanitizer.sanitize(event), event.occurredAt());
    }

    private Target target(DomainEvent event) {
        if (event instanceof RecordRevisionRestoredEvent restored) return new Target("RESTORE_OPERATION", restored.restoreOperationId());
        if (event instanceof AgentRunRequestedEvent requested) return new Target("AGENT_RUN", requested.runId());
        if (event instanceof AgentRunSucceededEvent succeeded) return new Target("AGENT_RUN", succeeded.runId());
        if (event instanceof AgentRunFailedEvent failed) return new Target("AGENT_RUN", failed.runId());
        if (event instanceof AgentArtifactViewedEvent viewed) return new Target("AGENT_ARTIFACT", viewed.artifactId());
        return null;
    }

    private record Target(String type, UUID id) {}
}
