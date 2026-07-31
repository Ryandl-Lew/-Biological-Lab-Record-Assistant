package com.bionote.restore;

import com.bionote.revision.RevisionDtos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class RestoreJsonCodec {
    private final ObjectMapper json;

    public RestoreJsonCodec(ObjectMapper json) {
        this.json = json;
    }

    public String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Restore data cannot be encoded", e);
        }
    }

    public RevisionDtos.DiffSummary decodeSummary(String value) {
        try {
            return json.readValue(value, RevisionDtos.DiffSummary.class);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid restore diff summary", e);
        }
    }
}
