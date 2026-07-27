package com.bionote.agent.trace;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AgentTraceRecorder {
    private final AgentStepRepository steps;private final TraceSanitizer sanitizer;
    public AgentTraceRecorder(AgentStepRepository steps,TraceSanitizer sanitizer){this.steps=steps;this.sanitizer=sanitizer;}
    public AgentStepRepository.Step record(UUID runId,String type,String toolName,Object request,Object response,long latencyMs,long inputTokens,long outputTokens){return steps.append(runId,type,toolName,sanitizer.sanitize(request),sanitizer.sanitize(response),latencyMs,inputTokens,outputTokens);}
}
