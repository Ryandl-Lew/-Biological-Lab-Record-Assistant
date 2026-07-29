package com.bionote.collaboration.event;

public interface DomainEventPublisher { void publish(DomainEvent event); }
