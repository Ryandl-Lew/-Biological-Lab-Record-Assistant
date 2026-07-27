package com.bionote.agent.prompt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class PromptVersionService {
    private final JdbcTemplate jdbc; private final ObjectMapper json;
    public PromptVersionService(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}

    @Transactional
    public PromptVersion register(String name,int version,String template,String schema,String policy,String hash,boolean active){
        List<PromptVersion> existing=jdbc.query("SELECT * FROM prompt_versions WHERE prompt_name=? AND version_no=?",(rs,n)->map(rs.getString("id"),rs.getString("prompt_name"),rs.getInt("version_no"),rs.getString("template_text"),rs.getString("output_schema_json"),rs.getString("tool_policy_json"),rs.getString("content_hash")),name,version);
        if(!existing.isEmpty()){
            PromptVersion value=existing.get(0);if(!value.contentHash().equals(hash))throw new IllegalStateException("Published prompt content changed without a new version: "+name+" v"+version);
            if(active)activate(value.id(),name);return value;
        }
        UUID id=UUID.randomUUID();if(active)jdbc.update("UPDATE prompt_versions SET active=FALSE,active_name_key=NULL WHERE prompt_name=? AND active=TRUE",name);
        jdbc.update("INSERT INTO prompt_versions(id,prompt_name,version_no,template_text,output_schema_json,tool_policy_json,content_hash,active,active_name_key,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)",id.toString(),name,version,template,schema,policy,hash,active,active?name:null,Timestamp.from(Instant.now()));
        return new PromptVersion(id,name,version,template,read(schema),read(policy),hash,allowed(read(policy)));
    }
    public PromptVersion get(UUID id){return jdbc.query("SELECT * FROM prompt_versions WHERE id=?",(rs,n)->map(rs.getString("id"),rs.getString("prompt_name"),rs.getInt("version_no"),rs.getString("template_text"),rs.getString("output_schema_json"),rs.getString("tool_policy_json"),rs.getString("content_hash")),id.toString()).stream().findFirst().orElseThrow(()->new IllegalStateException("Prompt version not found: "+id));}
    public PromptVersion active(String name){return jdbc.query("SELECT * FROM prompt_versions WHERE prompt_name=? AND active=TRUE",(rs,n)->map(rs.getString("id"),rs.getString("prompt_name"),rs.getInt("version_no"),rs.getString("template_text"),rs.getString("output_schema_json"),rs.getString("tool_policy_json"),rs.getString("content_hash")),name).stream().findFirst().orElseThrow(()->new IllegalStateException("Active prompt not found: "+name));}
    private void activate(UUID id,String name){jdbc.update("UPDATE prompt_versions SET active=FALSE,active_name_key=NULL WHERE prompt_name=? AND id<>? AND active=TRUE",name,id.toString());jdbc.update("UPDATE prompt_versions SET active=TRUE,active_name_key=? WHERE id=?",name,id.toString());}
    private PromptVersion map(String id,String name,int version,String template,String schema,String policy,String hash){JsonNode policyNode=read(policy);return new PromptVersion(UUID.fromString(id),name,version,template,read(schema),policyNode,hash,allowed(policyNode));}
    private JsonNode read(String value){try{return json.readTree(value);}catch(Exception e){throw new IllegalStateException("Invalid prompt JSON",e);}}
    private Set<String> allowed(JsonNode policy){Set<String> values=new LinkedHashSet<>();JsonNode tools=policy.path("allowedTools");if(tools.isArray())tools.forEach(value->values.add(value.asText()));return Set.copyOf(values);}
}
