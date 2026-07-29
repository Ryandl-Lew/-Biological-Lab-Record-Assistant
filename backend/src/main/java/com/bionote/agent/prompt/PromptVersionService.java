package com.bionote.agent.prompt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class PromptVersionService {
    private final PromptVersionStore prompts; private final ObjectMapper json;
    public PromptVersionService(PromptVersionStore prompts,ObjectMapper json){this.prompts=prompts;this.json=json;}

    @Transactional
    public PromptVersion register(String name,int version,String template,String schema,String policy,String hash,boolean active){
        var existing=prompts.findByNameAndVersion(name,version);
        if(existing.isPresent()){
            var value=existing.get();if(!value.contentHash().equals(hash))throw new IllegalStateException("Published prompt content changed without a new version: "+name+" v"+version);
            if(active)activate(value.id(),name);return map(value);
        }
        UUID id=UUID.randomUUID();if(active)prompts.deactivateAll(name);
        var value=new PromptVersionStore.StoredPromptVersion(id,name,version,template,schema,policy,hash,active,active?name:null,Instant.now());
        prompts.insert(value);return map(value);
    }
    public PromptVersion get(UUID id){return prompts.findById(id).map(this::map).orElseThrow(()->new IllegalStateException("Prompt version not found: "+id));}
    public PromptVersion active(String name){return prompts.findActive(name).map(this::map).orElseThrow(()->new IllegalStateException("Active prompt not found: "+name));}
    private void activate(UUID id,String name){prompts.deactivateOthers(name,id);prompts.activate(id,name);}
    private PromptVersion map(PromptVersionStore.StoredPromptVersion value){JsonNode policyNode=read(value.toolPolicyJson());return new PromptVersion(value.id(),value.name(),value.version(),value.templateText(),read(value.outputSchemaJson()),policyNode,value.contentHash(),allowed(policyNode));}
    private JsonNode read(String value){try{return json.readTree(value);}catch(Exception e){throw new IllegalStateException("Invalid prompt JSON",e);}}
    private Set<String> allowed(JsonNode policy){Set<String> values=new LinkedHashSet<>();JsonNode tools=policy.path("allowedTools");if(tools.isArray())tools.forEach(value->values.add(value.asText()));return Set.copyOf(values);}
}
