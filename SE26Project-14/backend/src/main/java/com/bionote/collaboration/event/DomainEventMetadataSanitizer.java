package com.bionote.collaboration.event;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DomainEventMetadataSanitizer {
    private static final int MAX_STRING_LENGTH = 500;
    private static final int MAX_COLLECTION_ITEMS = 20;
    private static final Map<String, Set<String>> SAFE_KEYS =
            Map.of(
                    "RECORD_REVISION_RESTORED",
                            Set.of(
                                    "revisionNo",
                                    "sourceRevisionId",
                                    "fromVersion",
                                    "toVersion",
                                    "changedSections",
                                    "attachmentAdded",
                                    "attachmentRemoved"),
                    "AGENT_RUN_REQUESTED", Set.of("runId", "artifactKind", "triggerType", "status"),
                    "AGENT_RUN_SUCCEEDED",
                            Set.of("runId", "artifactKind", "triggerType", "status", "artifactId"),
                    "AGENT_RUN_FAILED",
                            Set.of("runId", "artifactKind", "triggerType", "status", "errorCode"),
                    "AGENT_ARTIFACT_VIEWED", Set.of("runId", "artifactKind", "artifactId"));

    public Map<String, Object> sanitize(DomainEvent event) {
        Set<String> allowed = SAFE_KEYS.getOrDefault(event.eventType(), Set.of());
        Map<String, Object> safe = new LinkedHashMap<>();
        event.metadata()
                .forEach(
                        (key, value) -> {
                            if (!allowed.contains(key)) return;
                            Object sanitized = safeValue(value);
                            if (sanitized != null) safe.put(key, sanitized);
                        });
        return Map.copyOf(safe);
    }

    private Object safeValue(Object value) {
        if (value == null) return null;
        if (value instanceof UUID || value instanceof Enum<?>) return value.toString();
        if (value instanceof Number || value instanceof Boolean) return value;
        if (value instanceof CharSequence text) {
            String result = text.toString();
            return result.length() <= MAX_STRING_LENGTH
                    ? result
                    : result.substring(0, MAX_STRING_LENGTH);
        }
        if (value instanceof Collection<?> collection) {
            ArrayList<Object> result = new ArrayList<>();
            for (Object item : collection) {
                if (result.size() == MAX_COLLECTION_ITEMS) break;
                Object safe = safeValue(item);
                if (safe != null && !(safe instanceof Collection<?>)) result.add(safe);
            }
            return result;
        }
        return null;
    }
}
