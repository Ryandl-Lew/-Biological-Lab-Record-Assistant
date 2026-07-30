package com.bionote.agent.runtime;

import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AgentRunStateMachine {
    private static final Set<String> TERMINAL =
            Set.of("SUCCEEDED", "FAILED", "CANCELLED", "LIMIT_EXCEEDED", "INVALID_OUTPUT");
    private static final Map<String, Set<String>> ALLOWED =
            Map.of("QUEUED", Set.of("RUNNING", "CANCELLED"), "RUNNING", TERMINAL);

    public boolean terminal(String status) {
        return TERMINAL.contains(status);
    }

    public void require(String from, String to) {
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to))
            throw new IllegalStateException("Illegal agent run transition: " + from + " -> " + to);
    }
}
