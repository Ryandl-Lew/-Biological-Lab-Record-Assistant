package com.bionote.agent.tool;

import com.bionote.agent.runtime.AgentRunMemory;
import com.bionote.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;

@Component
public class AgentToolExecutor {
    private final AgentToolRegistry registry; private final ObjectMapper json; private final Validator validator;private final JdbcTemplate jdbc;
    public AgentToolExecutor(AgentToolRegistry registry,ObjectMapper json,Validator validator,JdbcTemplate jdbc){this.registry=registry;this.json=json;this.validator=validator;this.jdbc=jdbc;}

    public ExecutedToolResult execute(AgentToolContext context,String name,JsonNode arguments,Set<String> allowedTools,AgentRunMemory memory){
        if(Instant.now().isAfter(context.deadline()))throw new ApiException(HttpStatus.GATEWAY_TIMEOUT,"AGENT_TIMEOUT","Agent run deadline exceeded");
        if(context.cancelled().getAsBoolean())throw new ApiException(HttpStatus.CONFLICT,"AGENT_RUN_CANCELLED","Agent run was cancelled");
        if(!allowedTools.contains(name))throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"AGENT_TOOL_NOT_ALLOWED","Tool is not allowed by prompt policy: "+name);
        AgentTool<?,?> raw=registry.find(name).orElseThrow(()->new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"AGENT_UNKNOWN_TOOL","Unknown tool: "+name));
        AgentToolDefinition definition=raw.definition();if(!definition.artifactKinds().isEmpty()&&!definition.artifactKinds().contains(context.artifactKind()))throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"AGENT_TOOL_NOT_ALLOWED","Tool is not allowed for artifact kind");
        if(arguments==null||!arguments.isObject())throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"AGENT_TOOL_ARGUMENTS_INVALID","Tool arguments must be a JSON object");
        String argumentHash=hash(canonical(arguments)),cacheKey=name+":"+argumentHash;ExecutedToolResult cached=memory.cached(cacheKey);if(cached!=null){memory.addEvidenceCandidates(cached.evidenceCandidates());return cached.withCached(true);}
        int perToolLimit=perToolLimit(context.runId(),name);if(memory.toolCalls(name)>=perToolLimit)throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"AGENT_LIMIT_EXCEEDED","Tool call policy limit exceeded: "+name);memory.incrementToolCalls(name);
        ExecutedToolResult value=invoke(raw,context,arguments,definition,argumentHash);memory.cache(cacheKey,value);memory.addEvidenceCandidates(value.evidenceCandidates());return value;
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private ExecutedToolResult invoke(AgentTool raw,AgentToolContext context,JsonNode arguments,AgentToolDefinition definition,String argumentHash){try{Object input=json.treeToValue(arguments,raw.inputType());Set<ConstraintViolation<Object>> violations=validator.validate(input);if(!violations.isEmpty())throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"AGENT_TOOL_ARGUMENTS_INVALID",violations.iterator().next().getMessage());AgentToolResult result=raw.execute(context,input);JsonNode data=json.valueToTree(result.data());boolean truncated=false;if(data.isArray()&&data.size()>definition.maxItems()){com.fasterxml.jackson.databind.node.ArrayNode limited=json.createArrayNode();for(int i=0;i<definition.maxItems();i++)limited.add(data.get(i));data=limited;truncated=true;}String encoded=canonical(data);if(encoded.length()>definition.maxOutputChars()){data=json.getNodeFactory().textNode(encoded.substring(0,definition.maxOutputChars()));truncated=true;}String resultHash=hash(canonical(data));String summary=result.summary()==null?"":limit(result.summary(),500);return new ExecutedToolResult(name(raw),argumentHash,data,result.evidenceCandidates(),summary,resultHash,truncated,false);}catch(ApiException e){throw e;}catch(Exception e){throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"AGENT_TOOL_FAILED","Tool execution failed: "+definition.name());}}
    private String name(AgentTool<?,?> tool){return tool.definition().name();}
    private String canonical(JsonNode value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException(e);}}
    private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private String limit(String value,int max){return value.length()<=max?value:value.substring(0,max);}
    private int perToolLimit(java.util.UUID runId,String name){try{String policy=jdbc.queryForObject("SELECT pv.tool_policy_json FROM agent_runs ar JOIN prompt_versions pv ON pv.id=ar.prompt_version_id WHERE ar.id=?",String.class,runId.toString());JsonNode value=json.readTree(policy).path("maxCallsByTool").path(name);return value.isInt()&&value.asInt()>0?value.asInt():Integer.MAX_VALUE;}catch(Exception e){return Integer.MAX_VALUE;}}
    public record ExecutedToolResult(String toolName,String argumentHash,JsonNode data,java.util.List<String> evidenceCandidates,String summary,String resultHash,boolean truncated,boolean cached){public ExecutedToolResult withCached(boolean value){return new ExecutedToolResult(toolName,argumentHash,data,evidenceCandidates,summary,resultHash,truncated,value);}}
}
