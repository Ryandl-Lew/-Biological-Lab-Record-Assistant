package com.bionote.agent.runtime;

import com.bionote.agent.config.AgentProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class AgentRunWorker {
    private final AgentProperties properties;private final AgentRunStore runs;private final AgentRunLifecycleService lifecycle;private final AgentHarness harness;
    public AgentRunWorker(AgentProperties properties,AgentRunStore runs,AgentRunLifecycleService lifecycle,AgentHarness harness){this.properties=properties;this.runs=runs;this.lifecycle=lifecycle;this.harness=harness;}
    @Scheduled(fixedDelayString="${agent.worker-poll-ms:1000}") public void poll(){if(!properties.isEnabled())return;tick();}
    public boolean tick(){lifecycle.failStaleRunning(Instant.now().minusMillis(properties.getWorkerStaleTimeoutMs()));AgentRunRecord run=runs.claimNext();if(run==null)return false;harness.execute(run.id());return true;}
}
