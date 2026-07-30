package com.bionote.agent.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record EvidenceCandidate(
        String type,
        String id,
        UUID projectId,
        UUID recordId,
        UUID revisionId,
        UUID reviewId,
        UUID fromRevisionId,
        UUID toRevisionId,
        String sourceHash,
        String label) {
    public static EvidenceCandidate project(UUID projectId, String label) {
        return new EvidenceCandidate(
                "PROJECT",
                projectId.toString(),
                projectId,
                null,
                null,
                null,
                null,
                null,
                null,
                label);
    }

    public static EvidenceCandidate record(UUID projectId, UUID recordId, String label) {
        return new EvidenceCandidate(
                "实验记录",
                recordId.toString(),
                projectId,
                recordId,
                null,
                null,
                null,
                null,
                null,
                label);
    }

    public static EvidenceCandidate revision(
            UUID projectId, UUID recordId, UUID revisionId, String label) {
        return new EvidenceCandidate(
                "版本",
                revisionId.toString(),
                projectId,
                recordId,
                revisionId,
                null,
                null,
                null,
                null,
                label);
    }

    public static EvidenceCandidate review(
            UUID projectId, UUID recordId, UUID revisionId, UUID reviewId, String label) {
        return new EvidenceCandidate(
                "审核",
                reviewId.toString(),
                projectId,
                recordId,
                revisionId,
                reviewId,
                null,
                null,
                null,
                label);
    }

    public static EvidenceCandidate audit(
            UUID projectId, UUID recordId, UUID auditId, String label) {
        return new EvidenceCandidate(
                "活动", auditId.toString(), projectId, recordId, null, null, null, null, null, label);
    }

    public static EvidenceCandidate diff(
            UUID projectId, UUID recordId, UUID from, UUID to, String sourceHash, String label) {
        return new EvidenceCandidate(
                "版本对比",
                recordId + ":" + from + ":" + to,
                projectId,
                recordId,
                null,
                null,
                from,
                to,
                sourceHash,
                label);
    }

    public JsonNode publicView(ObjectMapper json) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("type", type);
        value.put("id", id);
        if (recordId != null) value.put("recordId", recordId);
        if (revisionId != null) value.put("revisionId", revisionId);
        if (reviewId != null) value.put("reviewId", reviewId);
        if (fromRevisionId != null) value.put("fromRevisionId", fromRevisionId);
        if (toRevisionId != null) value.put("toRevisionId", toRevisionId);
        value.put("label", label);
        return json.valueToTree(value);
    }

    public String encode(ObjectMapper json) {
        try {
            return json.writeValueAsString(this);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static EvidenceCandidate decode(ObjectMapper json, String value) {
        try {
            return json.readValue(value, EvidenceCandidate.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid evidence candidate", e);
        }
    }
}
