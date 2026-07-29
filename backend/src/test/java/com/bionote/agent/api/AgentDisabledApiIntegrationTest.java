package com.bionote.agent.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={"agent.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentDisabledApiIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;
    @Test void disabledFeatureReturnsStableErrorWithoutProviderAccess()throws Exception{MvcResult registered=mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Disabled Agent\",\"email\":\"disabled-"+UUID.randomUUID()+"@example.com\",\"password\":\"Password123!\"}" )).andExpect(status().isOk()).andReturn();String token=value(registered,"data.accessToken");mvc.perform(post("/api/v1/records/{id}/agent-runs",UUID.randomUUID()).header("Authorization","Bearer "+token).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content("{\"artifactKind\":\"RECORD_SUMMARY\"}" )).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("AGENT_DISABLED"));mvc.perform(post("/api/v1/records/{id}/agent-chat",UUID.randomUUID()).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hello\"}" )).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("AGENT_DISABLED"));mvc.perform(post("/api/v1/projects/{id}/agent-chat",UUID.randomUUID()).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hello\"}" )).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("AGENT_DISABLED"));}
    private String value(MvcResult result,String path)throws Exception{JsonNode node=json.readTree(result.getResponse().getContentAsString());for(String part:path.split("\\."))node=node.get(part);return node.asText();}
}
