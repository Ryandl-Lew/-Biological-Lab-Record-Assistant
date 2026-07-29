package com.bionote.config;

import com.bionote.agent.api.AgentDtos;
import com.bionote.agent.api.AgentRunService;
import com.bionote.agent.config.AgentProperties;
import com.bionote.agent.runtime.AgentRunWorker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Component
@Profile({"local", "dev"})
@ConditionalOnProperty(name={"bionote.dev-seed-enabled","agent.enabled"},havingValue="true")
public class DemoAgentDataSeeder {
    private static final Set<String> TERMINAL=Set.of("SUCCEEDED","FAILED","CANCELLED","LIMIT_EXCEEDED","INVALID_OUTPUT");
    private final DemoDataStore demoDataStore;private final AgentRunService service;private final AgentRunWorker worker;private final AgentProperties properties;
    public DemoAgentDataSeeder(DemoDataStore demoDataStore,AgentRunService service,AgentRunWorker worker,AgentProperties properties){this.demoDataStore=demoDataStore;this.service=service;this.worker=worker;this.properties=properties;}

    @EventListener(ApplicationReadyEvent.class)
    @Order(100)
    public void seed(){if(!"fake".equals(properties.getProvider()))return;var projectId=demoDataStore.findProjectIdByName("HepG2 缺氧响应 qPCR");var ownerId=demoDataStore.findUserIdByEmail("member@example.com");if(projectId.isEmpty()||ownerId.isEmpty())return;UUID project=projectId.get(),owner=ownerId.get();ensureTerminal(owner,service.createProject(owner,project,new AgentDtos.CreateRunRequest("PROJECT_PROGRESS",Instant.parse("2026-07-01T00:00:00Z"),Instant.parse("2026-07-27T00:00:00Z"),"演示项目进展"),"demo:phase2:project-progress"));ensureTerminal(owner,service.createProject(owner,project,new AgentDtos.CreateRunRequest("PROJECT_PROGRESS",Instant.parse("2026-07-01T00:00:00Z"),Instant.parse("2026-07-27T00:00:00Z"),"__FAKE_SCENARIO__:evidence-invalid"),"demo:phase2:failed-run"));}
    private void ensureTerminal(UUID actor,AgentDtos.RunView initial){AgentDtos.RunView current=initial;for(int attempt=0;attempt<12&&!TERMINAL.contains(current.status());attempt++){worker.tick();current=service.get(actor,current.id());}}
}
