package com.bionote.collaboration;

import com.bionote.audit.AuditEventAppender;
import com.bionote.notification.NotificationAppender;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class EventService implements CollaborationEvents {
    private final NotificationAppender notifications; private final AuditEventAppender auditWriter;
    public EventService(NotificationAppender notifications,AuditEventAppender auditWriter){this.notifications=notifications;this.auditWriter=auditWriter;}

    @Override public void audit(UUID actor, UUID project, UUID record, String type, String targetType, UUID target, Map<String,?> metadata){
        auditWriter.append(UUID.randomUUID(),actor,project,record,type,targetType,target,metadata,Instant.now());
    }
    @Override public void notify(UUID recipient, String type, String title, String body, Map<String,?> payload, String dedupKey){
        notifications.appendIfAbsent(UUID.randomUUID(),recipient,type,title,body,payload,dedupKey,Instant.now());
    }
}
