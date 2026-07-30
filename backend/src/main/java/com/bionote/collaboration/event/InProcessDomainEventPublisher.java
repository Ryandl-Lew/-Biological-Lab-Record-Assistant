package com.bionote.collaboration.event;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class InProcessDomainEventPublisher implements DomainEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(InProcessDomainEventPublisher.class);
    private final List<DomainEventHandler<?>> handlers;

    public InProcessDomainEventPublisher(List<DomainEventHandler<?>> discoveredHandlers) {
        Map<String, DomainEventHandler<?>> unique = new LinkedHashMap<>();
        for (DomainEventHandler<?> handler : discoveredHandlers) {
            String key =
                    handler.getClass().getName()
                            + "|"
                            + handler.eventType().getName()
                            + "|"
                            + handler.phase();
            unique.putIfAbsent(key, handler);
        }
        this.handlers =
                unique.values().stream()
                        .sorted(
                                Comparator.comparing(DomainEventHandler<?>::phase)
                                        .thenComparingInt(DomainEventHandler::order)
                                        .thenComparing(handler -> handler.getClass().getName()))
                        .toList();
    }

    @Override
    public void publish(DomainEvent event) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(event.eventId(), "event.eventId");
        Objects.requireNonNull(event.eventType(), "event.eventType");
        Objects.requireNonNull(event.occurredAt(), "event.occurredAt");
        List<DomainEventHandler<?>> transactional =
                matching(event, HandlerPhase.TRANSACTIONAL_REQUIRED);
        List<DomainEventHandler<?>> afterCommit =
                matching(event, HandlerPhase.AFTER_COMMIT_BEST_EFFORT);
        transactional.forEach(handler -> invokeRequired(handler, event));
        if (afterCommit.isEmpty()) return;
        Runnable callbacks = () -> afterCommit.forEach(handler -> invokeBestEffort(handler, event));
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            callbacks.run();
                        }
                    });
        } else callbacks.run();
    }

    private List<DomainEventHandler<?>> matching(DomainEvent event, HandlerPhase phase) {
        List<DomainEventHandler<?>> result = new ArrayList<>();
        for (DomainEventHandler<?> handler : handlers) {
            if (handler.phase() == phase && handler.eventType().isInstance(event))
                result.add(handler);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private void invokeRequired(DomainEventHandler<?> handler, DomainEvent event) {
        ((DomainEventHandler<DomainEvent>) handler).handle(event);
    }

    @SuppressWarnings("unchecked")
    private void invokeBestEffort(DomainEventHandler<?> handler, DomainEvent event) {
        try {
            ((DomainEventHandler<DomainEvent>) handler).handle(event);
        } catch (RuntimeException exception) {
            log.warn(
                    "Best-effort domain event handler failed: eventId={}, eventType={}, handler={}",
                    event.eventId(),
                    event.eventType(),
                    handler.getClass().getSimpleName());
        }
    }
}
