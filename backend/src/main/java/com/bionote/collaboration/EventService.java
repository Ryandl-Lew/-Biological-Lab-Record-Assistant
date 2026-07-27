package com.bionote.collaboration;

import com.bionote.collaboration.event.AuditEventWriter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class EventService {
    private final JdbcTemplate jdbc; private final ObjectMapper json; private final AuditEventWriter auditWriter;
    public EventService(JdbcTemplate jdbc, ObjectMapper json, AuditEventWriter auditWriter){this.jdbc=jdbc;this.json=json;this.auditWriter=auditWriter;}
    private String encode(Map<String,?> value){try{return json.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalArgumentException(e);}}

    public void audit(UUID actor, UUID project, UUID record, String type, String targetType, UUID target, Map<String,?> metadata){
        auditWriter.append(UUID.randomUUID(),actor,project,record,type,targetType,target,metadata,Instant.now());
    }
    public void notify(UUID recipient, String type, String title, String body, Map<String,?> payload, String dedupKey){
        try { jdbc.update("INSERT INTO notifications(id,recipient_id,type,title,body,payload_json,dedup_key,created_at) VALUES(?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(), recipient.toString(), type, title, body, encode(payload), dedupKey, Timestamp.from(Instant.now())); }
        catch (DuplicateKeyException ignored) { }
    }
}
