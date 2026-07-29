package com.bionote.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AuditJsonCodec {
    private final ObjectMapper json;
    public AuditJsonCodec(ObjectMapper json){this.json=json;}
    public String encode(Map<String,?> value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("Audit metadata cannot be encoded",e);}}
    public Map<String,Object> decode(String value){try{return json.readValue(value,new TypeReference<>(){});}catch(Exception e){return Map.of();}}
}
