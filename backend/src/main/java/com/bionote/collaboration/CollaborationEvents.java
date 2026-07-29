package com.bionote.collaboration;

import java.util.Map;
import java.util.UUID;

public interface CollaborationEvents {
    void audit(UUID actor,UUID project,UUID record,String type,String targetType,UUID target,Map<String,?> metadata);
    void notify(UUID recipient,String type,String title,String body,Map<String,?> payload,String dedupKey);
}
