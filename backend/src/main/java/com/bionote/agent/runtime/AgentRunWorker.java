package com.bionote.agent.runtime;

import com.bionote.agent.config.AgentProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class AgentRunWorker {
    private final AgentProperties properties;private final AgentRunRepository runs;private final AgentHarness harness;
    public AgentRunWorker(AgentProperties properties,AgentRunRepository runs,AgentHarness harness){this.properties=properties;this.runs=runs;this.harness=harness;}
    @Scheduled(fixedDelayString="${agent.worker-poll-ms:1000}") public void poll(){if(!properties.isEnabled())return;tick();}
    public boolean tick(){runs.failStaleRunning(Instant.now().minusMillis(properties.getWorkerStaleTimeoutMs()));AgentRunRecord run=runs.claimNext();if(run==null)return false;harness.execute(run.id());return true;}
}
