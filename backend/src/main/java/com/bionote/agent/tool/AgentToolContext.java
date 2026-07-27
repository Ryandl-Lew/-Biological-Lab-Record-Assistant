package com.bionote.agent.tool;

import java.time.Instant;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public record AgentToolContext(UUID runId,UUID actorId,UUID projectId,UUID recordId,
                               String subjectType,UUID subjectId,String artifactKind,
                               Instant deadline,BooleanSupplier cancelled) {}
