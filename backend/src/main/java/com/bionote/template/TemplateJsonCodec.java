package com.bionote.template;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TemplateJsonCodec {
    private final ObjectMapper json;

    public TemplateJsonCodec(ObjectMapper json) {
        this.json = json;
    }

    public String encode(Object value) {
        try {
            return value == null ? null : json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public Object decodeValue(String value) {
        try {
            return value == null ? null : json.readValue(value, Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    public List<String> decodeOptions(String value) {
        try {
            return value == null ? List.of() : json.readValue(value, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
