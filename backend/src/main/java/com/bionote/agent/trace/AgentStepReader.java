package com.bionote.agent.trace;

import java.util.List;
import java.util.UUID;

public interface AgentStepReader {
    List<AgentStepData> list(UUID runId);
}
