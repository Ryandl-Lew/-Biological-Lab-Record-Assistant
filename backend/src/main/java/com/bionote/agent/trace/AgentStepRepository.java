package com.bionote.agent.trace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Repository
public class AgentStepRepository {
    private final JdbcTemplate jdbc;private final ObjectMapper json;
    public AgentStepRepository(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    @Transactional public Step append(UUID runId,String type,String toolName,JsonNode request,JsonNode response,long latency,long inputTokens,long outputTokens){Integer current=jdbc.queryForObject("SELECT step_count FROM agent_runs WHERE id=? FOR UPDATE",Integer.class,runId.toString());if(current==null)throw new IllegalStateException("Agent run not found");int no=current+1;String requestJson=encode(request),responseJson=encode(response),hash=hash(type+"|"+toolName+"|"+requestJson+"|"+responseJson);UUID id=UUID.randomUUID();Instant now=Instant.now();jdbc.update("INSERT INTO agent_steps(id,run_id,step_no,step_type,tool_name,request_json,response_json,content_hash,latency_ms,input_tokens,output_tokens,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",id.toString(),runId.toString(),no,type,toolName,requestJson,responseJson,hash,Math.max(0,latency),Math.max(0,inputTokens),Math.max(0,outputTokens),Timestamp.from(now));jdbc.update("UPDATE agent_runs SET step_count=?,input_tokens=input_tokens+?,output_tokens=output_tokens+? WHERE id=?",no,Math.max(0,inputTokens),Math.max(0,outputTokens),runId.toString());return new Step(id,runId,no,type,toolName,request,response,hash,latency,inputTokens,outputTokens,now);}
    public List<Step> list(UUID runId){return jdbc.query("SELECT * FROM agent_steps WHERE run_id=? ORDER BY step_no",(rs,n)->new Step(UUID.fromString(rs.getString("id")),runId,rs.getInt("step_no"),rs.getString("step_type"),rs.getString("tool_name"),read(rs.getString("request_json")),read(rs.getString("response_json")),rs.getString("content_hash"),rs.getLong("latency_ms"),rs.getLong("input_tokens"),rs.getLong("output_tokens"),rs.getTimestamp("created_at").toInstant()),runId.toString());}
    private String encode(JsonNode value){try{return value==null||value.isNull()?null:json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException(e);}}
    private JsonNode read(String value){try{return value==null?null:json.readTree(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    public record Step(UUID id,UUID runId,int stepNo,String stepType,String toolName,JsonNode request,JsonNode response,String contentHash,long latencyMs,long inputTokens,long outputTokens,Instant createdAt){}
}
