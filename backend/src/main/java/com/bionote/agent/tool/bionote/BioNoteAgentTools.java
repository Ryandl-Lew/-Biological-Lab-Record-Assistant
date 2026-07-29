package com.bionote.agent.tool.bionote;

import com.bionote.agent.tool.AgentTool;
import com.bionote.agent.tool.AgentToolDefinition;
import com.bionote.agent.tool.AgentToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Configuration
public class BioNoteAgentTools {
    private static final Set<String> BOTH=Set.of("RECORD_SUMMARY","PROJECT_PROGRESS"),PROJECT=Set.of("PROJECT_PROGRESS");
    @Bean AgentTool<ProjectOverviewInput,Object> projectOverviewTool(BioNoteAgentReadService reads,ObjectMapper json){return tool("get_project_overview","Read project status and aggregate counts",ProjectOverviewInput.class,input(json,Map.of("includeMemberCounts","boolean"),List.of()),BOTH,(c,i)->reads.projectOverview(c));}
    @Bean AgentTool<ListRecordsInput,Object> listProjectRecordsTool(BioNoteAgentReadService reads,ObjectMapper json){return tool("list_project_records","List bounded project record summaries",ListRecordsInput.class,input(json,Map.of("statuses","array","updatedFrom","string","updatedTo","string","page","integer","size","integer"),List.of()),PROJECT,(c,i)->reads.listRecords(c,i.statuses,i.updatedFrom,i.updatedTo,i.page,i.size));}
    @Bean AgentTool<RecordInput,Object> recordOverviewTool(BioNoteAgentReadService reads,ObjectMapper json){return tool("get_record_overview","Read one record overview without full body",RecordInput.class,input(json,Map.of("recordId","string"),List.of()),BOTH,(c,i)->reads.recordOverview(c,i.recordId));}
    @Bean AgentTool<RecordWindowInput,Object> listRecordRevisionsTool(BioNoteAgentReadService reads,ObjectMapper json){return tool("list_record_revisions","List at most twenty revision summaries",RecordWindowInput.class,input(json,Map.of("recordId","string","updatedFrom","string","updatedTo","string"),List.of()),BOTH,(c,i)->reads.listRevisions(c,i.recordId,i.updatedFrom,i.updatedTo));}
    @Bean AgentTool<RevisionInput,Object> revisionSummaryTool(BioNoteAgentReadService reads,ObjectMapper json){return tool("get_revision_summary","Read a bounded revision summary",RevisionInput.class,input(json,Map.of("revisionId","string"),List.of("revisionId")),BOTH,(c,i)->reads.revisionSummary(c,i.revisionId));}
    @Bean AgentTool<DiffInput,Object> compareRecordRevisionsTool(BioNoteAgentReadService reads,ObjectMapper json){return tool("compare_record_revisions","Compare two revisions through the domain diff service",DiffInput.class,input(json,Map.of("fromRevisionId","string","toRevisionId","string","includeTextHunks","boolean"),List.of("fromRevisionId","toRevisionId")),BOTH,(c,i)->reads.compare(c,i.fromRevisionId,i.toRevisionId,i.includeTextHunks));}
    @Bean AgentTool<RecordInput,Object> reviewFeedbackTool(BioNoteAgentReadService reads,ObjectMapper json){return tool("list_review_feedback","List bounded review feedback for a record",RecordInput.class,input(json,Map.of("recordId","string"),List.of()),BOTH,(c,i)->reads.reviews(c,i.recordId));}
    @Bean AgentTool<ActivityInput,Object> projectActivityTool(BioNoteAgentReadService reads,ObjectMapper json){return tool("list_project_activity","List sanitized project audit activity",ActivityInput.class,input(json,Map.of("eventTypes","array","updatedFrom","string","updatedTo","string","page","integer","size","integer"),List.of()),PROJECT,(c,i)->reads.activity(c,i.eventTypes,i.updatedFrom,i.updatedTo,i.page,i.size));}
    @Bean AgentTool<EmptyInput,Object> latestProjectReportTool(BioNoteAgentReadService reads,ObjectMapper json){return tool("get_latest_project_report","Read headline and period of the latest derived project report",EmptyInput.class,input(json,Map.of(),List.of()),PROJECT,(c,i)->reads.latestReport(c));}
    private <I> AgentTool<I,Object> tool(String name,String description,Class<I> type,com.fasterxml.jackson.databind.JsonNode schema,Set<String> kinds,Runner<I> runner){return new AgentTool<>(){public AgentToolDefinition definition(){return new AgentToolDefinition(name,description,schema,20000,50,kinds,AgentToolDefinition.SideEffect.READ_ONLY);}public Class<I> inputType(){return type;}public AgentToolResult<Object> execute(com.bionote.agent.tool.AgentToolContext context,I input){BioNoteAgentReadService.ToolPayload value=runner.run(context,input);return new AgentToolResult<>(value.data(),value.candidates(),description);}};}
    private com.fasterxml.jackson.databind.node.ObjectNode input(ObjectMapper json,Map<String,String> fields,List<String> required){var node=json.createObjectNode().put("type","object").put("additionalProperties",false);var properties=json.createObjectNode();fields.forEach((name,type)->{var property=json.createObjectNode().put("type",type);if("array".equals(type))property.set("items",json.createObjectNode().put("type","string"));if("size".equals(name)){property.put("minimum",1);property.put("maximum",50);}if("page".equals(name))property.put("minimum",0);properties.set(name,property);});node.set("properties",properties);var values=json.createArrayNode();required.forEach(values::add);node.set("required",values);return node;}
    @FunctionalInterface private interface Runner<I>{BioNoteAgentReadService.ToolPayload run(com.bionote.agent.tool.AgentToolContext context,I input);}
    public record EmptyInput(){} public record ProjectOverviewInput(Boolean includeMemberCounts){}
    public record RecordInput(UUID recordId){} public record RecordWindowInput(UUID recordId,Instant updatedFrom,Instant updatedTo){}
    public record RevisionInput(@NotNull UUID revisionId){} public record DiffInput(@NotNull UUID fromRevisionId,@NotNull UUID toRevisionId,boolean includeTextHunks){}
    public record ListRecordsInput(List<String> statuses,Instant updatedFrom,Instant updatedTo,@Min(0) int page,@Min(1) @Max(50) int size){public ListRecordsInput{if(size==0)size=20;}}
    public record ActivityInput(List<String> eventTypes,Instant updatedFrom,Instant updatedTo,@Min(0) int page,@Min(1) @Max(50) int size){public ActivityInput{if(size==0)size=20;}}
}
