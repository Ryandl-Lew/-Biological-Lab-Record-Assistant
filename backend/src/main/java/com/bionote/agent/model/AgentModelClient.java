package com.bionote.agent.model;

public interface AgentModelClient {
    String provider();
    ModelCapabilities capabilities();
    AgentModelResponse complete(AgentModelRequest request);
}
