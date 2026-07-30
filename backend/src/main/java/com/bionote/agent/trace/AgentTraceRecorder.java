package com.bionote.agent.trace;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AgentTraceRecorder {
    private final AgentStepAppender steps;
    private final TraceSanitizer sanitizer;

    public AgentTraceRecorder(AgentStepAppender steps, TraceSanitizer sanitizer) {
        this.steps = steps;
        this.sanitizer = sanitizer;
    }

    public AgentStepData record(
            UUID runId,
            String type,
            String toolName,
            Object request,
            Object response,
            long latencyMs,
            long inputTokens,
            long outputTokens) {
        return steps.append(
                runId,
                type,
                toolName,
                sanitizer.sanitize(request),
                sanitizer.sanitize(response),
                latencyMs,
                inputTokens,
                outputTokens);
    }
}
