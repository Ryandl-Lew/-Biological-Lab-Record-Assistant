package com.bionote.agent.validation;

import com.bionote.agent.report.EvidenceCandidate;
import com.bionote.agent.runtime.AgentRunContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Repairs provider output without inventing facts or evidence. */
@Component
public class AgentArtifactNormalizer {
    private static final Pattern EMAIL =
            Pattern.compile("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");
    private final ObjectMapper json;

    public AgentArtifactNormalizer(ObjectMapper json) {
        this.json = json;
    }

    public JsonNode normalize(AgentRunContext context, JsonNode candidate, String reason) {
        JsonNode source =
                candidate != null && candidate.isObject() ? candidate : json.createObjectNode();
        ObjectNode out = json.createObjectNode();
        out.put("schemaVersion", source.path("schemaVersion").asInt(2));
        out.put(
                "headline",
                text(
                        source.path("headline"),
                        "RECORD_SUMMARY".equals(context.run().artifactKind())
                                ? "实验记录总结"
                                : "项目进展总结",
                        200));
        out.put(
                "executiveSummary",
                text(
                        source.path("executiveSummary"),
                        "已完成本次只读分析。以下内容仅基于当前可访问的项目数据与证据。",
                        2000));

        EvidenceIndex evidence = evidence(context, source.path("evidence"));
        out.set("progress", factualItems(source.path("progress"), evidence, false));
        out.set("risks", factualItems(source.path("risks"), evidence, true));
        out.set("nextActions", nextActions(source.path("nextActions"), evidence));
        out.set("evidence", evidence.values());

        ArrayNode limitations = strings(source.path("limitations"), 30, 1000);
        if (reason != null && !reason.isBlank() && limitations.size() < 30) {
            limitations.add(text(reason, 1000));
        }
        if (evidence.values().isEmpty() && limitations.isEmpty()) {
            limitations.add("本次运行未获得可引用证据，报告仅保留安全的基础说明。");
        }
        out.set("limitations", limitations);
        out.set("period", period(context, source.path("period")));
        return out;
    }

    public boolean hasUnknownDeclaredEvidence(AgentRunContext context, JsonNode candidate) {
        if (candidate == null || !candidate.isObject() || !candidate.path("evidence").isArray()) {
            return false;
        }
        Set<String> knownIds = new HashSet<>();
        for (String encoded : context.memory().evidenceCandidates()) {
            try {
                knownIds.add(EvidenceCandidate.decode(json, encoded).id());
            } catch (Exception ignored) {
                // Malformed runtime evidence cannot authorize a model-declared citation.
            }
        }
        for (JsonNode item : candidate.path("evidence")) {
            String id = item.path("id").asText();
            if (!id.isBlank() && !knownIds.contains(id)) return true;
        }
        return false;
    }

    private EvidenceIndex evidence(AgentRunContext context, JsonNode declared) {
        Map<String, String> requestedRefs = new HashMap<>();
        if (declared.isArray()) {
            for (JsonNode item : declared) {
                String id = item.path("id").asText();
                String ref = safeRef(item.path("ref").asText());
                if (!id.isBlank() && !ref.isBlank()) requestedRefs.putIfAbsent(id, ref);
            }
        }

        ArrayNode values = json.createArrayNode();
        Map<String, String> refsByIdentity = new LinkedHashMap<>();
        Map<String, String> oldToNew = new HashMap<>();
        Set<String> usedRefs = new HashSet<>();
        int index = 1;
        for (String encoded : context.memory().evidenceCandidates()) {
            if (values.size() >= 100) break;
            try {
                EvidenceCandidate candidate = EvidenceCandidate.decode(json, encoded);
                String type = canonicalType(candidate);
                String identity = type + "|" + candidate.id();
                if (refsByIdentity.containsKey(identity)) continue;
                String requested = requestedRefs.getOrDefault(candidate.id(), "");
                String ref;
                if (!requested.isBlank() && !usedRefs.contains(requested)) {
                    ref = requested;
                } else {
                    do {
                        ref = "e" + index++;
                    } while (usedRefs.contains(ref));
                }
                usedRefs.add(ref);
                refsByIdentity.put(identity, ref);
                if (!requested.isBlank()) oldToNew.put(requested, ref);

                ObjectNode value = json.createObjectNode();
                value.put("ref", ref);
                value.put("type", type);
                value.put("id", text(candidate.id(), 160));
                if (candidate.recordId() != null)
                    value.put("recordId", candidate.recordId().toString());
                if (candidate.revisionId() != null)
                    value.put("revisionId", candidate.revisionId().toString());
                if (candidate.reviewId() != null)
                    value.put("reviewId", candidate.reviewId().toString());
                if (candidate.fromRevisionId() != null)
                    value.put("fromRevisionId", candidate.fromRevisionId().toString());
                if (candidate.toRevisionId() != null)
                    value.put("toRevisionId", candidate.toRevisionId().toString());
                value.put("label", text(candidate.label(), type + " " + candidate.id(), 300));
                values.add(value);
            } catch (Exception ignored) {
                // Invalid candidates are omitted; the resulting report remains structurally safe.
            }
        }
        return new EvidenceIndex(values, refsByIdentity, oldToNew);
    }

    private ArrayNode factualItems(JsonNode source, EvidenceIndex evidence, boolean risk) {
        ArrayNode out = json.createArrayNode();
        if (!source.isArray() || evidence.values().isEmpty()) return out;
        int index = 1;
        for (JsonNode item : source) {
            if (out.size() >= 30 || !item.isObject()) break;
            String statement = text(item.path("statement"), "", 1000);
            if (statement.isBlank()) continue;
            ArrayNode refs = refs(item.path("evidenceRefs"), evidence, true);
            if (refs.isEmpty()) continue;
            ObjectNode value = json.createObjectNode();
            value.put(
                    "id",
                    text(item.path("id"), (risk ? "risk-" : "progress-") + index++, 60));
            value.put("statement", statement);
            if (risk) {
                String severity = item.path("severity").asText().toUpperCase();
                value.put(
                        "severity",
                        Set.of("LOW", "MEDIUM", "HIGH").contains(severity)
                                ? severity
                                : "MEDIUM");
            }
            value.set("evidenceRefs", refs);
            out.add(value);
        }
        return out;
    }

    private ArrayNode nextActions(JsonNode source, EvidenceIndex evidence) {
        ArrayNode out = json.createArrayNode();
        if (!source.isArray()) return out;
        int index = 1;
        for (JsonNode item : source) {
            if (out.size() >= 30 || !item.isObject()) break;
            String statement = text(item.path("statement"), "", 1000);
            if (statement.isBlank()) continue;
            ArrayNode refs = refs(item.path("evidenceRefs"), evidence, false);
            String basis = item.path("basis").asText();
            if (!"REVIEW_FEEDBACK".equals(basis) || refs.isEmpty()) basis = "MODEL_SUGGESTION";
            ObjectNode value = json.createObjectNode();
            value.put("id", text(item.path("id"), "action-" + index++, 60));
            value.put("statement", statement);
            value.put("basis", basis);
            value.set("evidenceRefs", refs);
            out.add(value);
        }
        return out;
    }

    private ArrayNode refs(JsonNode source, EvidenceIndex evidence, boolean required) {
        ArrayNode out = json.createArrayNode();
        Set<String> known = new HashSet<>(evidence.refsByIdentity().values());
        if (source.isArray()) {
            for (JsonNode item : source) {
                if (out.size() >= 20) break;
                String original = safeRef(item.asText());
                String ref = evidence.oldToNew().getOrDefault(original, original);
                if (!ref.isBlank() && known.contains(ref) && !contains(out, ref)) out.add(ref);
            }
        }
        if (required && out.isEmpty() && !known.isEmpty()) out.add(known.iterator().next());
        return out;
    }

    private ObjectNode period(AgentRunContext context, JsonNode source) {
        JsonNode request;
        try {
            request = json.readTree(context.run().requestJson());
        } catch (Exception e) {
            request = json.createObjectNode();
        }
        String fallbackEnd = request.path("periodEnd").asText(Instant.now().toString());
        String fallbackStart = request.path("periodStart").asText(fallbackEnd);
        ObjectNode out = json.createObjectNode();
        out.put("start", text(source.path("start"), fallbackStart, 40));
        out.put("end", text(source.path("end"), fallbackEnd, 40));
        return out;
    }

    private ArrayNode strings(JsonNode source, int maxItems, int maxLength) {
        ArrayNode out = json.createArrayNode();
        if (!source.isArray()) return out;
        for (JsonNode item : source) {
            if (out.size() >= maxItems) break;
            String value = text(item, "", maxLength);
            if (!value.isBlank()) out.add(value);
        }
        return out;
    }

    private String canonicalType(EvidenceCandidate value) {
        if (Set.of("PROJECT", "RECORD", "REVISION", "REVIEW", "AUDIT_EVENT", "REVISION_DIFF")
                .contains(value.type())) return value.type();
        if (value.fromRevisionId() != null || value.toRevisionId() != null) return "REVISION_DIFF";
        if (value.reviewId() != null) return "REVIEW";
        if (value.revisionId() != null) return "REVISION";
        if (value.recordId() != null) return "RECORD";
        if (value.projectId() != null && value.projectId().toString().equals(value.id()))
            return "PROJECT";
        return "AUDIT_EVENT";
    }

    private boolean contains(ArrayNode values, String target) {
        for (JsonNode value : values) if (target.equals(value.asText())) return true;
        return false;
    }

    private String safeRef(String value) {
        return text(value, 60).replaceAll("[^A-Za-z0-9._:-]", "_");
    }

    private String text(JsonNode value, String fallback, int max) {
        return text(value != null && value.isValueNode() ? value.asText() : null, fallback, max);
    }

    private String text(String value, int max) {
        return text(value, "", max);
    }

    private String text(String value, String fallback, int max) {
        String result = value == null || value.isBlank() ? fallback : value.trim();
        result = EMAIL.matcher(result).replaceAll("[redacted]");
        result = result.replace("admin_delete_record", "restricted operation");
        return result.length() <= max ? result : result.substring(0, max);
    }

    private record EvidenceIndex(
            ArrayNode values, Map<String, String> refsByIdentity, Map<String, String> oldToNew) {}
}
