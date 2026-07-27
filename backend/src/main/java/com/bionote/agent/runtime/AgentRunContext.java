package com.bionote.agent.runtime;

import com.bionote.agent.prompt.PromptVersion;

import java.time.Instant;

public record AgentRunContext(AgentRunRecord run,PromptVersion prompt,AgentRunMemory memory,
                              AgentLimits limits,Instant deadline) {}
