package com.bionote.agent.model;

import com.bionote.agent.config.AgentProperties;
import com.bionote.agent.tool.AgentToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name="agent.provider",havingValue="openai-compatible")
public class OpenAiCompatibleModelClient implements AgentModelClient {
    private final AgentProperties properties;private final ObjectMapper json;private final RestClient client;
    public OpenAiCompatibleModelClient(AgentProperties properties,ObjectMapper json){this.properties=properties;this.json=json;if(properties.isEnabled()&&(blank(properties.getBaseUrl())||blank(properties.getApiKey())||blank(properties.getModel())))throw new IllegalStateException("AGENT_BASE_URL, AGENT_API_KEY and AGENT_MODEL are required when the real agent provider is enabled");String base=blank(properties.getBaseUrl())?"http://127.0.0.1/":properties.getBaseUrl().replaceAll("/*$","/");var factory=new org.springframework.http.client.JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofMillis(properties.getTimeoutMs())).build());factory.setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()));this.client=RestClient.builder().baseUrl(base).requestFactory(factory).build();}
    @Override public String provider(){return "openai-compatible";}@Override public ModelCapabilities capabilities(){return new ModelCapabilities(true,true);}
    @Override public AgentModelResponse complete(AgentModelRequest request){if(blank(properties.getApiKey()))throw new ModelClientException("MODEL_PROVIDER_UNAVAILABLE","Agent provider is not configured");try{Map<String,Object> body=new LinkedHashMap<>();body.put("model",properties.getModel());body.put("messages",messages(request));body.put("tools",request.tools().stream().map(this::tool).toList());body.put("tool_choice","auto");body.put("temperature",0);body.put("max_tokens",request.maxOutputTokens());JsonNode root=client.post().uri("chat/completions").header(HttpHeaders.AUTHORIZATION,"Bearer "+properties.getApiKey()).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);return normalize(root);}catch(RestClientResponseException e){int status=e.getStatusCode().value();String code=status==429?"AGENT_RATE_LIMITED":status==401||status==403?"MODEL_PROVIDER_UNAVAILABLE":status>=500?"MODEL_PROVIDER_UNAVAILABLE":"MODEL_PROVIDER_ERROR";throw new ModelClientException(code,"Model provider request failed with status "+status);}catch(ResourceAccessException e){throw new ModelClientException("AGENT_TIMEOUT","Model provider timed out",e);}catch(ModelClientException e){throw e;}catch(Exception e){throw new ModelClientException("MODEL_PROVIDER_UNAVAILABLE","Model provider response could not be processed",e);}}
    List<Map<String,Object>> messages(AgentModelRequest request){List<Map<String,Object>> values=new ArrayList<>();String system=request.systemPrompt();if(request.outputSchema()!=null&&!request.outputSchema().isNull()&&!request.outputSchema().isMissingNode()&&!request.outputSchema().isEmpty())system=system+"\n\nOUTPUT_JSON_SCHEMA (return one JSON object only):\n"+request.outputSchema();values.add(Map.of("role","system","content",system));for(var message:request.messages()){Map<String,Object> value=new LinkedHashMap<>();value.put("role",message.role());value.put("content",message.content());if(message.toolCallId()!=null)value.put("tool_call_id",message.toolCallId());if(message.toolName()!=null)value.put("name",message.toolName());if(!message.toolCalls().isEmpty())value.put("tool_calls",message.toolCalls().stream().map(call->Map.of("id",call.id(),"type","function","function",Map.of("name",call.name(),"arguments",call.arguments().toString()))).toList());values.add(value);}return values;}
    private Map<String,Object> tool(AgentToolDefinition definition){return Map.of("type","function","function",Map.of("name",definition.name(),"description",definition.description(),"parameters",definition.inputSchema()));}
    private AgentModelResponse normalize(JsonNode root)throws Exception{JsonNode choice=root.path("choices").path(0).path("message");List<ModelToolCall> calls=new ArrayList<>();for(JsonNode call:choice.path("tool_calls")){JsonNode function=call.path("function");JsonNode arguments=function.path("arguments").isTextual()?json.readTree(function.path("arguments").asText()):function.path("arguments");calls.add(new ModelToolCall(call.path("id").asText(),function.path("name").asText(),arguments));}JsonNode output=null;if(calls.isEmpty()&&choice.hasNonNull("content")){output=parseJsonContent(choice.path("content").asText());}JsonNode usage=root.path("usage");return new AgentModelResponse(calls,output,usage.path("prompt_tokens").asLong(0),usage.path("completion_tokens").asLong(0),null,null);}
    JsonNode parseJsonContent(String content)throws Exception{String value=content==null?"":content.trim();if(value.startsWith("```")&&value.endsWith("```")){int newline=value.indexOf('\n');if(newline>2){String language=value.substring(3,newline).trim();if(language.isEmpty()||"json".equalsIgnoreCase(language))value=value.substring(newline+1,value.length()-3).trim();}}return json.readTree(value);}
    private boolean blank(String value){return value==null||value.isBlank();}
}
