package com.bionote.record;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RecordJsonCodec {
    private final ObjectMapper json;

    public RecordJsonCodec(ObjectMapper json) {
        this.json = json;
    }

    public String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public Object decode(String value) {
        try {
            return json.readValue(value, Object.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    public Map<String, Object> decodeMap(String value) {
        try {
            return json.readValue(value, new TypeReference<>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }
}
