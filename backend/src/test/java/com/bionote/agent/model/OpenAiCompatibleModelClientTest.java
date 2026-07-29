package com.bionote.agent.model;

import com.bionote.agent.config.AgentProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleModelClientTest {
    @Test void disabledUnconfiguredAdapterCanExistButCannotCall(){AgentProperties properties=new AgentProperties();properties.setEnabled(false);properties.setProvider("openai-compatible");properties.setBaseUrl("");properties.setApiKey("");OpenAiCompatibleModelClient client=new OpenAiCompatibleModelClient(properties,new ObjectMapper());assertThatThrownBy(()->client.complete(null)).isInstanceOf(ModelClientException.class).extracting(e->((ModelClientException)e).code()).isEqualTo("MODEL_PROVIDER_UNAVAILABLE");}
    @Test void enabledAdapterRequiresAllConfiguration(){AgentProperties properties=new AgentProperties();properties.setEnabled(true);properties.setProvider("openai-compatible");assertThatThrownBy(()->new OpenAiCompatibleModelClient(properties,new ObjectMapper())).isInstanceOf(IllegalStateException.class).hasMessageContaining("Base URL");}
    @Test void includesOutputSchemaInProviderSystemInstruction(){AgentProperties properties=new AgentProperties();properties.setEnabled(false);properties.setProvider("openai-compatible");ObjectMapper json=new ObjectMapper();OpenAiCompatibleModelClient client=new OpenAiCompatibleModelClient(properties,json);AgentModelRequest request=new AgentModelRequest(UUID.randomUUID(),UUID.randomUUID(),"model","system",List.of(new AgentModelRequest.ModelMessage("user","synthetic",null,null)),List.of(),json.createObjectNode().put("type","object"),100);assertThat(client.messages(request).get(0).get("content").toString()).contains("OUTPUT_JSON_SCHEMA","\"type\":\"object\"");}
    @Test void acceptsOnlyWholeJsonCodeFence()throws Exception{AgentProperties properties=new AgentProperties();properties.setEnabled(false);ObjectMapper json=new ObjectMapper();OpenAiCompatibleModelClient client=new OpenAiCompatibleModelClient(properties,json);assertThat(client.parseJsonContent("```json\n{\"schemaVersion\":1}\n```").path("schemaVersion").asInt()).isEqualTo(1);assertThat(client.parseJsonContent("explanation\n```json\n{\"schemaVersion\":2}\n```").path("schemaVersion").asInt()).isEqualTo(2);assertThatThrownBy(()->client.parseJsonContent("not-json-at-all")).isInstanceOf(Exception.class);}
}
