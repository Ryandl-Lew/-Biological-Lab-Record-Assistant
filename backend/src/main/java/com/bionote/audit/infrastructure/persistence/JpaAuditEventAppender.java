package com.bionote.audit.infrastructure.persistence;

import com.bionote.audit.AuditEventAppender;
import com.bionote.audit.AuditJsonCodec;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Repository
@Transactional
public class JpaAuditEventAppender implements AuditEventAppender {
    private final AuditEventJpaRepository events;private final AuditJsonCodec json;
    public JpaAuditEventAppender(AuditEventJpaRepository events,AuditJsonCodec json){this.events=events;this.json=json;}
    @Override public void append(UUID eventId,UUID actorId,UUID projectId,UUID recordId,String eventType,String targetType,UUID targetId,Map<String,?> metadata,Instant occurredAt){events.saveAndFlush(new AuditEventEntity(eventId,actorId,projectId,recordId,eventType,targetType,targetId,json.encode(metadata),occurredAt));}
    @Override public void appendOnce(UUID eventId,UUID actorId,UUID projectId,UUID recordId,String eventType,String targetType,UUID targetId,Map<String,?> metadata,Instant occurredAt){events.appendOnce(value(eventId),value(actorId),value(projectId),value(recordId),eventType,targetType,value(targetId),json.encode(metadata),occurredAt);AuditEventEntity stored=events.findById(eventId).orElseThrow();if(!eventType.equals(stored.eventType)||!targetType.equals(stored.targetType)||!targetId.equals(stored.targetId))throw new DataIntegrityViolationException("Audit event id already belongs to another event");}
    private String value(UUID id){return id==null?null:id.toString();}
}
