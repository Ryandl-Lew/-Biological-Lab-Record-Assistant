package com.bionote.agent.runtime;

import com.bionote.agent.config.AgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public record AgentLimits(int maxSteps,int maxToolCalls,int maxModelCalls,long maxDurationMs,
                          int maxOutputTokens,int maxRepairTurns) {
    public static AgentLimits defaults(AgentProperties p){return new AgentLimits(p.getMaxSteps(),p.getMaxToolCalls(),p.getMaxModelCalls(),p.getMaxDurationMs(),p.getMaxOutputTokens(),p.getMaxRepairTurns());}
    public static AgentLimits parse(ObjectMapper json,String value){try{JsonNode n=json.readTree(value);return new AgentLimits(n.path("maxSteps").asInt(30),n.path("maxToolCalls").asInt(12),n.path("maxModelCalls").asInt(10),n.path("maxDurationMs").asLong(120000),n.path("maxOutputTokens").asInt(2000),n.path("maxRepairTurns").asInt(1));}catch(Exception e){throw new IllegalStateException("Invalid agent limits",e);}}
}
