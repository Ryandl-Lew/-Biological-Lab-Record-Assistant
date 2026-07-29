package com.bionote.agent.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record AgentModelResponse(List<ModelToolCall> toolCalls, JsonNode finalOutput,
                                 long inputTokens, long outputTokens,
                                 String errorCode, String errorMessage) {
    public AgentModelResponse { toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls); }
    public static AgentModelResponse tools(List<ModelToolCall> calls){return new AgentModelResponse(calls,null,0,0,null,null);}
    public static AgentModelResponse finish(JsonNode output){return new AgentModelResponse(List.of(),output,0,0,null,null);}
    public static AgentModelResponse error(String code,String message){return new AgentModelResponse(List.of(),null,0,0,code,message);}
}
