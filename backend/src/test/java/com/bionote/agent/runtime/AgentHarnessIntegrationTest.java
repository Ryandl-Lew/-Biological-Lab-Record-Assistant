package com.bionote.agent.runtime;

import com.bionote.agent.model.AgentModelRequest;
import com.bionote.agent.model.AgentModelResponse;
import com.bionote.agent.model.FakeAgentModelClient;
import com.bionote.agent.model.ModelClientException;
import com.bionote.agent.model.ModelToolCall;
import com.bionote.agent.prompt.PromptVersion;
import com.bionote.agent.prompt.PromptVersionService;
import com.bionote.agent.tool.AgentTool;
import com.bionote.agent.tool.AgentToolContext;
import com.bionote.agent.tool.AgentToolDefinition;
import com.bionote.agent.tool.AgentToolResult;
import com.bionote.agent.trace.AgentStepRepository;
import com.bionote.collaboration.event.AgentRunSucceededEvent;
import com.bionote.collaboration.event.DomainEventHandler;
import com.bionote.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import({AgentHarnessIntegrationTest.ToolsConfig.class,AgentHarnessIntegrationTest.FailureConfig.class})
class AgentHarnessIntegrationTest {
    @Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;@Autowired AgentRunRepository runs;@Autowired AgentHarness harness;@Autowired FakeAgentModelClient fake;@Autowired PromptVersionService prompts;@Autowired AgentStepRepository steps;@Autowired AtomicInteger counter;@Autowired AtomicBoolean failAgentArtifactCommit;
    private UUID user,project;
    @BeforeEach void setup(){
        jdbc.update("UPDATE experiment_records SET current_review_id=NULL, final_revision_id=NULL");
        jdbc.update("DELETE FROM agent_artifacts");
        jdbc.update("DELETE FROM agent_steps");
        jdbc.update("DELETE FROM agent_runs");
        jdbc.update("DELETE FROM record_restore_operations");
        jdbc.update("DELETE FROM revision_attachments");
        jdbc.update("DELETE FROM reviews");
        jdbc.update("DELETE FROM record_revisions");
        jdbc.update("DELETE FROM attachments");
        jdbc.update("DELETE FROM experiment_records");
        jdbc.update("DELETE FROM audit_events");
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM project_invitations");
        jdbc.update("DELETE FROM project_members");
        jdbc.update("DELETE FROM projects");
        jdbc.update("DELETE FROM users");
        counter.set(0);failAgentArtifactCommit.set(false);user=UUID.randomUUID();project=UUID.randomUUID();Instant now=Instant.now();jdbc.update("INSERT INTO users(id,display_name,email_normalized,password_hash,created_at,updated_at,version) VALUES(?,?,?,?,?,?,0)",user.toString(),"Agent Tester",user+"@example.com","hash",Timestamp.from(now),Timestamp.from(now));jdbc.update("INSERT INTO projects(id,name,status,owner_id,created_at,updated_at,version) VALUES(?,?,'ACTIVE',?,?,?,0)",project.toString(),"Runtime Project",user.toString(),Timestamp.from(now),Timestamp.from(now));jdbc.update("INSERT INTO project_members(project_id,user_id,role,joined_at) VALUES(?,?, 'OWNER',?)",project.toString(),user.toString(),Timestamp.from(now));}

    @AfterEach void cleanAgentData(){cleanupRuns();}

    @Test void toolCallsUseCacheAndProduceValidatedArtifact(){JsonNodeBuilder args=new JsonNodeBuilder(json).put("key","same");fake.script(List.of(AgentModelResponse.tools(List.of(new ModelToolCall("c1","runtime_counter",args.node()))),AgentModelResponse.tools(List.of(new ModelToolCall("c2","runtime_counter",args.node()))),AgentModelResponse.finish(valid("done"))));AgentRunRecord run=execute(limits(30,12,10,1));assertThat(run.status()).isEqualTo("SUCCEEDED");assertThat(counter.get()).isEqualTo(1);assertThat(runs.artifact(run.id()).path("summary").asText()).isEqualTo("done");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agent_artifacts WHERE run_id=?",Integer.class,run.id().toString())).isEqualTo(1);List<AgentStepRepository.Step> trace=steps.list(run.id());assertThat(trace).extracting(AgentStepRepository.Step::stepNo).containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1,trace.size()).boxed().toList());assertThat(trace.stream().filter(step->"TOOL_RESULT".equals(step.stepType())).map(step->step.response().path("cached").asBoolean()).toList()).containsExactly(false,true);assertThat(fake.requests()).hasSize(3).allSatisfy(request->{assertThat(request.promptVersionId()).isNotNull();assertThat(request.tools()).extracting(AgentToolDefinition::name).contains("runtime_counter");});}

    @Test void cachedToolResultExplicitlyTellsModelNotToRepeat(){JsonNodeBuilder args=new JsonNodeBuilder(json).put("key","same");fake.script(List.of(AgentModelResponse.tools(List.of(new ModelToolCall("c1","runtime_counter",args.node()))),AgentModelResponse.tools(List.of(new ModelToolCall("c2","runtime_counter",args.node()))),AgentModelResponse.finish(valid("done"))));assertThat(execute(limits(10,5,5,0)).status()).isEqualTo("SUCCEEDED");List<AgentModelRequest.ModelMessage> toolMessages=fake.requests().get(2).messages().stream().filter(message->"tool".equals(message.role())).toList();assertThat(toolMessages).hasSize(2);assertThat(toolMessages.get(1).content()).contains("\"cached\":true").contains("do not request the same tool");}

    @Test void traceEntriesDoNotConsumeExecutionStepBudget(){fake.script(List.of(AgentModelResponse.tools(List.of(new ModelToolCall("a","runtime_echo",new JsonNodeBuilder(json).put("value","one").node()))),AgentModelResponse.tools(List.of(new ModelToolCall("b","runtime_echo",new JsonNodeBuilder(json).put("value","two").node()))),AgentModelResponse.finish(valid("within budget"))));AgentRunRecord run=execute(limits(5,5,5,0));assertThat(run.status()).isEqualTo("SUCCEEDED");assertThat(run.stepCount()).isGreaterThan(5);assertThat(fake.requests()).hasSize(3);}

    @Test void executionStepLimitStillTerminatesToolLoops(){fake.script(List.of(AgentModelResponse.tools(List.of(new ModelToolCall("a","runtime_echo",new JsonNodeBuilder(json).put("value","one").node()))),AgentModelResponse.finish(valid("too late"))));AgentRunRecord run=execute(limits(2,5,5,0));assertThat(run.status()).isEqualTo("LIMIT_EXCEEDED");assertThat(run.errorMessage()).contains("execution step limit");assertThat(fake.requests()).hasSize(1);}

    @Test void executesTwoDifferentToolsInOrder(){fake.script(List.of(AgentModelResponse.tools(List.of(new ModelToolCall("a","runtime_echo",new JsonNodeBuilder(json).put("value","hello").node()),new ModelToolCall("b","runtime_counter",new JsonNodeBuilder(json).put("key","x").node()))),AgentModelResponse.finish(valid("two"))));AgentRunRecord run=execute(limits(30,12,10,1));assertThat(run.status()).isEqualTo("SUCCEEDED");assertThat(steps.list(run.id()).stream().filter(step->"TOOL_CALL".equals(step.stepType())).map(AgentStepRepository.Step::toolName).toList()).containsExactly("runtime_echo","runtime_counter");}

    @Test void oneRepairCanSucceedButSecondInvalidOutputTerminates(){fake.script(List.of(AgentModelResponse.finish(json.createObjectNode().put("summary","missing evidence")),AgentModelResponse.finish(valid("repaired"))));assertThat(execute(limits(20,5,5,1)).status()).isEqualTo("SUCCEEDED");cleanupRuns();fake.script(List.of(AgentModelResponse.finish(json.createObjectNode().put("summary","bad1")),AgentModelResponse.finish(json.createObjectNode().put("summary","bad2"))));AgentRunRecord failed=execute(limits(20,5,5,1));assertThat(failed.status()).isEqualTo("INVALID_OUTPUT");}

    @Test void unknownToolInvalidArgumentsProviderTimeoutAndToolFailureAreStableFailures(){fake.script(List.of(AgentModelResponse.tools(List.of(new ModelToolCall("x","not_registered",json.createObjectNode())))));assertFailure("FAILED","AGENT_UNKNOWN_TOOL");cleanupRuns();fake.script(List.of(AgentModelResponse.tools(List.of(new ModelToolCall("x","runtime_echo",json.createArrayNode())))));assertFailure("FAILED","AGENT_TOOL_ARGUMENTS_INVALID");cleanupRuns();fake.script(List.of(new ModelClientException("AGENT_TIMEOUT","timeout")));assertFailure("FAILED","AGENT_TIMEOUT");cleanupRuns();fake.script(List.of(AgentModelResponse.tools(List.of(new ModelToolCall("x","runtime_echo",new JsonNodeBuilder(json).put("value","fail").node())))));assertFailure("FAILED","AGENT_TOOL_FAILED");}

    @Test void limitsAndCancellationReachDeterministicTerminalStates(){fake.script(List.of(AgentModelResponse.tools(List.of(new ModelToolCall("x","runtime_counter",new JsonNodeBuilder(json).put("key","x").node())))));AgentRunRecord limited=execute(limits(20,5,1,0));assertThat(limited.status()).isEqualTo("LIMIT_EXCEEDED");cleanupRuns();AgentRunRecord queued=enqueue(limits(20,5,5,0));AgentRunRecord running=runs.claimNext();runs.requestCancel(running.id());harness.execute(running.id());assertThat(runs.load(queued.id()).status()).isEqualTo("CANCELLED");}

    @Test void twoWorkersClaimOnlyOnceAndStaleRunningIsFailed(){AgentRunRecord queued=enqueue(limits(20,5,5,0));CountDownLatch start=new CountDownLatch(1);List<CompletableFuture<AgentRunRecord>> claims=List.of(1,2).stream().map(ignore->CompletableFuture.supplyAsync(()->{try{start.await(5,TimeUnit.SECONDS);return runs.claimNext();}catch(Exception e){throw new RuntimeException(e);}})).toList();start.countDown();List<AgentRunRecord> results=claims.stream().map(CompletableFuture::join).toList();assertThat(results.stream().filter(java.util.Objects::nonNull).count()).isEqualTo(1);jdbc.update("UPDATE agent_runs SET started_at=? WHERE id=?",Timestamp.from(Instant.now().minusSeconds(3600)),queued.id().toString());assertThat(runs.failStaleRunning(Instant.now().minusSeconds(60))).isEqualTo(1);assertThat(runs.load(queued.id()).status()).isEqualTo("FAILED");}

    @Test void providerFailureAfterToolKeepsTraceAndNeverCreatesArtifact(){fake.script(List.of(AgentModelResponse.tools(List.of(new ModelToolCall("x","runtime_counter",new JsonNodeBuilder(json).put("key","x").node()))),new ModelClientException("MODEL_PROVIDER_UNAVAILABLE","provider unavailable")));AgentRunRecord failed=execute(limits(20,5,5,0));assertThat(failed.status()).isEqualTo("FAILED");assertThat(failed.errorCode()).isEqualTo("MODEL_PROVIDER_UNAVAILABLE");assertThat(steps.list(failed.id())).anyMatch(step->"TOOL_RESULT".equals(step.stepType()));assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agent_artifacts WHERE run_id=?",Integer.class,failed.id().toString())).isZero();}

    @Test void cancellationBeforeArtifactPersistenceWinsAndCreatesNoArtifact(){AgentRunRecord queued=enqueue(limits(20,5,5,0));AgentRunRecord running=runs.claimNext();runs.requestCancel(running.id());assertThatThrownBy(()->runs.completeWithArtifact(running.id(),valid("late"),json.createArrayNode().add("e1"))).isInstanceOfSatisfying(ApiException.class,error->assertThat(error.code()).isEqualTo("AGENT_RUN_CANCELLED"));harness.execute(queued.id());assertThat(runs.load(queued.id()).status()).isEqualTo("CANCELLED");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agent_artifacts WHERE run_id=?",Integer.class,queued.id().toString())).isZero();}

    @Test void artifactTransactionFailureRollsBackArtifactAndSucceededState(){failAgentArtifactCommit.set(true);fake.script(List.of(AgentModelResponse.finish(valid("rollback"))));AgentRunRecord failed=execute(limits(20,5,5,0));assertThat(failed.status()).isEqualTo("FAILED");assertThat(failed.errorCode()).isEqualTo("AGENT_RUNTIME_FAILED");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agent_artifacts WHERE run_id=?",Integer.class,failed.id().toString())).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE target_id=? AND event_type='AGENT_RUN_SUCCEEDED'",Integer.class,failed.id().toString())).isZero();}

    private AgentRunRecord execute(String limits){AgentRunRecord queued=enqueue(limits);AgentRunRecord claimed=runs.claimNext();assertThat(claimed.id()).isEqualTo(queued.id());harness.execute(claimed.id());return runs.load(claimed.id());}
    private AgentRunRecord enqueue(String limits){PromptVersion prompt=prompts.active("runtime-test");UUID id=UUID.randomUUID();return runs.enqueue(id,"PROJECT_PROGRESS","PROJECT",project,project,null,user,"MANUAL","fake","fake-deterministic-v1",prompt.id(),null,UUID.randomUUID().toString(),"{}","a".repeat(64),"{}",limits);}
    private void assertFailure(String status,String code){AgentRunRecord run=execute(limits(20,5,5,0));assertThat(run.status()).isEqualTo(status);String actual=jdbc.queryForObject("SELECT error_code FROM agent_runs WHERE id=?",String.class,run.id().toString());assertThat(actual).isEqualTo(code);}
    private void cleanupRuns(){jdbc.update("DELETE FROM agent_artifacts");jdbc.update("DELETE FROM agent_steps");jdbc.update("DELETE FROM agent_runs");}
    private String limits(int steps,int tools,int models,int repairs){try{return json.writeValueAsString(Map.of("maxSteps",steps,"maxToolCalls",tools,"maxModelCalls",models,"maxDurationMs",30000,"maxOutputTokens",500,"maxRepairTurns",repairs));}catch(Exception e){throw new RuntimeException(e);}}
    private com.fasterxml.jackson.databind.JsonNode valid(String summary){return json.createObjectNode().put("summary",summary).set("evidence",json.createArrayNode().add("e1"));}
    private static final class JsonNodeBuilder{private final com.fasterxml.jackson.databind.node.ObjectNode node;JsonNodeBuilder(ObjectMapper json){node=json.createObjectNode();}JsonNodeBuilder put(String key,String value){node.put(key,value);return this;}com.fasterxml.jackson.databind.JsonNode node(){return node;}}

    record EchoInput(@NotBlank String value){} record CounterInput(@NotBlank String key){}
    @TestConfiguration static class ToolsConfig{
        @Bean AtomicInteger runtimeCounter(){return new AtomicInteger();}
        @Bean AgentTool<EchoInput,Map<String,Object>> runtimeEchoTool(ObjectMapper json){return new AgentTool<>(){public AgentToolDefinition definition(){return new AgentToolDefinition("runtime_echo","Echoes a test value",json.createObjectNode().put("type","object"),2000,10,Set.of("PROJECT_PROGRESS"),AgentToolDefinition.SideEffect.READ_ONLY);}public Class<EchoInput> inputType(){return EchoInput.class;}public AgentToolResult<Map<String,Object>> execute(AgentToolContext context,EchoInput input){if("fail".equals(input.value()))throw new IllegalStateException("test failure");return AgentToolResult.of(Map.of("value",input.value()),"echoed");}};}
        @Bean AgentTool<CounterInput,Map<String,Object>> runtimeCounterTool(ObjectMapper json,AtomicInteger counter){return new AgentTool<>(){public AgentToolDefinition definition(){return new AgentToolDefinition("runtime_counter","Counts deterministic executions",json.createObjectNode().put("type","object"),2000,10,Set.of("PROJECT_PROGRESS"),AgentToolDefinition.SideEffect.READ_ONLY);}public Class<CounterInput> inputType(){return CounterInput.class;}public AgentToolResult<Map<String,Object>> execute(AgentToolContext context,CounterInput input){return AgentToolResult.of(Map.of("key",input.key(),"count",counter.incrementAndGet()),"counted");}};}
    }
    @TestConfiguration static class FailureConfig{
        @Bean AtomicBoolean failAgentArtifactCommit(){return new AtomicBoolean(false);}
        @Bean DomainEventHandler<AgentRunSucceededEvent> failingAgentSuccessHandler(AtomicBoolean failAgentArtifactCommit){return new DomainEventHandler<>(){public Class<AgentRunSucceededEvent> eventType(){return AgentRunSucceededEvent.class;}public int order(){return 1000;}public void handle(AgentRunSucceededEvent event){if(failAgentArtifactCommit.get())throw new IllegalStateException("intentional artifact transaction failure");}};}
    }
}
