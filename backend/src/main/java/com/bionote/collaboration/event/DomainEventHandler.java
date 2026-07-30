package com.bionote.collaboration.event;

public interface DomainEventHandler<E extends DomainEvent> {
    Class<E> eventType();

    default HandlerPhase phase() {
        return HandlerPhase.TRANSACTIONAL_REQUIRED;
    }

    default int order() {
        return 0;
    }

    void handle(E event);
}
