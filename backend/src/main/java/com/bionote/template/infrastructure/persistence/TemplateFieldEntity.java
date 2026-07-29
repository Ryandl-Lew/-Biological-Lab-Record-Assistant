package com.bionote.template.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "template_fields")
class TemplateFieldEntity {
    @Id @JdbcTypeCode(SqlTypes.CHAR) UUID id;
    @Column(name = "template_id", nullable = false) @JdbcTypeCode(SqlTypes.CHAR) UUID templateId;
    @Column(name = "field_key", nullable = false, length = 100) String fieldKey;
    @Column(nullable = false, length = 160) String label;
    @Column(name = "field_type", nullable = false, length = 30) String fieldType;
    @Column(nullable = false) boolean required;
    @Column(name = "sort_order", nullable = false) int sortOrder;
    @Column(length = 300) String placeholder;
    @Column(name = "default_value_json", columnDefinition = "TEXT") String defaultValueJson;
    @Column(name = "options_json", columnDefinition = "TEXT") String optionsJson;

    protected TemplateFieldEntity() {}

    TemplateFieldEntity(UUID id, UUID templateId, String fieldKey, String label, String fieldType,
                        boolean required, int sortOrder, String placeholder, String defaultValueJson,
                        String optionsJson) {
        this.id = id;
        this.templateId = templateId;
        this.fieldKey = fieldKey;
        this.label = label;
        this.fieldType = fieldType;
        this.required = required;
        this.sortOrder = sortOrder;
        this.placeholder = placeholder;
        this.defaultValueJson = defaultValueJson;
        this.optionsJson = optionsJson;
    }
}
