package com.bionote.agent.validation;

import com.bionote.agent.report.EvidenceCandidate;
import com.bionote.agent.runtime.AgentRunContext;
import com.bionote.audit.AuditQueryStore;
import com.bionote.project.ProjectMemberStore;
import com.bionote.record.RecordStore;
import com.bionote.review.ReviewStore;
import com.bionote.revision.RevisionLookup;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class EvidenceValidator {
    private static final Pattern EMAIL =
            Pattern.compile("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");
    private final ProjectMemberStore members;
    private final RecordStore records;
    private final RevisionLookup revisions;
    private final ReviewStore reviews;
    private final AuditQueryStore audit;
    private final ObjectMapper json;

    public EvidenceValidator(
            ProjectMemberStore members,
            RecordStore records,
            RevisionLookup revisions,
            ReviewStore reviews,
            AuditQueryStore audit,
            ObjectMapper json) {
        this.members = members;
        this.records = records;
        this.revisions = revisions;
        this.reviews = reviews;
        this.audit = audit;
        this.json = json;
    }

    public List<String> validate(AgentRunContext context, JsonNode artifact) {
        List<String> errors = new ArrayList<>();
        if (EMAIL.matcher(artifact.toString()).find())
            errors.add("EVIDENCE: artifact must not contain email addresses");
        if (artifact.toString().contains("admin_delete_record"))
            errors.add("EVIDENCE: artifact contains a forbidden tool claim");
        if (!member(context.run().projectId(), context.run().requestedBy()))
            errors.add("EVIDENCE: requester no longer has project access");
        Map<String, JsonNode> declared = new HashMap<>();
        for (JsonNode evidence : artifact.path("evidence")) {
            String ref = evidence.path("ref").asText();
            if (ref.isBlank()) errors.add("EVIDENCE: evidence ref is required");
            else if (declared.putIfAbsent(ref, evidence) != null)
                errors.add("EVIDENCE: duplicate evidence ref " + ref);
        }
        for (String section : List.of("progress", "risks"))
            for (JsonNode item : artifact.path(section)) {
                if (!item.path("evidenceRefs").isArray() || item.path("evidenceRefs").isEmpty())
                    errors.add("EVIDENCE: " + section + " item requires evidence");
                checkRefs(item, declared, errors);
            }
        for (JsonNode item : artifact.path("nextActions")) {
            checkRefs(item, declared, errors);
            if ("REVIEW_FEEDBACK".equals(item.path("basis").asText())
                    && item.path("evidenceRefs").isEmpty())
                errors.add("EVIDENCE: review-driven action requires evidence");
        }
        Map<String, EvidenceCandidate> candidates = new HashMap<>();
        for (String encoded : context.memory().evidenceCandidates()) {
            try {
                EvidenceCandidate value = EvidenceCandidate.decode(json, encoded);
                candidates.put(value.type() + "|" + value.id(), value);
            } catch (Exception e) {
                errors.add("EVIDENCE: invalid tool candidate");
            }
        }
        for (JsonNode value : declared.values()) {
            String type = value.path("type").asText(), id = value.path("id").asText();
            EvidenceCandidate candidate = candidates.get(type + "|" + id);
            if (candidate == null) {
                errors.add("EVIDENCE: evidence was not produced by this run: " + type + " " + id);
                continue;
            }
            if (!candidate.projectId().equals(context.run().projectId())) {
                errors.add("EVIDENCE: candidate is outside run project");
                continue;
            }
            match(value, "recordId", candidate.recordId(), errors);
            match(value, "revisionId", candidate.revisionId(), errors);
            match(value, "reviewId", candidate.reviewId(), errors);
            match(value, "fromRevisionId", candidate.fromRevisionId(), errors);
            match(value, "toRevisionId", candidate.toRevisionId(), errors);
            validateObject(context, candidate, errors);
        }
        return errors;
    }

    private void checkRefs(JsonNode item, Map<String, JsonNode> declared, List<String> errors) {
        for (JsonNode ref : item.path("evidenceRefs"))
            if (!declared.containsKey(ref.asText()))
                errors.add("EVIDENCE: unknown ref " + ref.asText());
    }

    private void match(JsonNode value, String field, UUID expected, List<String> errors) {
        if (value.has(field)
                && (expected == null || !expected.toString().equals(value.path(field).asText())))
            errors.add("EVIDENCE: " + field + " does not match tool evidence");
    }

    private void validateObject(AgentRunContext c, EvidenceCandidate e, List<String> errors) {
        try {
            switch (e.type()) {
                case "PROJECT" -> {
                    if (!e.id().equals(c.run().projectId().toString()))
                        errors.add("EVIDENCE: project mismatch");
                }
                case "RECORD" -> {
                    if (!activeRecordInProject(UUID.fromString(e.id()), c.run().projectId()))
                        errors.add("EVIDENCE: record is inaccessible");
                }
                case "REVISION" -> {
                    var revision = revisions.findById(UUID.fromString(e.id()));
                    if (revision.isEmpty()
                            || !activeRecordInProject(
                                    revision.get().recordId(), c.run().projectId()))
                        errors.add("EVIDENCE: revision is inaccessible");
                }
                case "REVIEW" -> {
                    var review = reviews.findById(UUID.fromString(e.id()));
                    if (review.isEmpty()
                            || !activeRecordInProject(review.get().recordId(), c.run().projectId()))
                        errors.add("EVIDENCE: review is inaccessible");
                }
                case "AUDIT_EVENT" -> {
                    if (!audit.exists(UUID.fromString(e.id()), c.run().projectId()))
                        errors.add("EVIDENCE: audit event is inaccessible");
                }
                case "REVISION_DIFF" -> {
                    if (e.fromRevisionId() == null
                            || e.toRevisionId() == null
                            || e.recordId() == null) {
                        errors.add("EVIDENCE: invalid revision diff");
                        break;
                    }
                    var from = revisions.findById(e.fromRevisionId());
                    var to = revisions.findById(e.toRevisionId());
                    if (from.isEmpty()
                            || to.isEmpty()
                            || !from.get().recordId().equals(e.recordId())
                            || !to.get().recordId().equals(e.recordId())
                            || !activeRecordInProject(e.recordId(), c.run().projectId())) {
                        errors.add("EVIDENCE: revision diff sources are inaccessible");
                        break;
                    }
                    String actual = sha(from.get().contentHash() + ":" + to.get().contentHash());
                    if (!actual.equals(e.sourceHash()))
                        errors.add("EVIDENCE: revision diff source hash changed");
                }
                default -> errors.add("EVIDENCE: unsupported type " + e.type());
            }
        } catch (Exception ex) {
            errors.add("EVIDENCE: object validation failed");
        }
    }

    private boolean activeRecordInProject(UUID recordId, UUID projectId) {
        return records.findActive(recordId)
                .map(value -> value.projectId().equals(projectId))
                .orElse(false);
    }

    private boolean member(UUID project, UUID actor) {
        return members.findRole(project, actor).isPresent();
    }

    private String sha(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
