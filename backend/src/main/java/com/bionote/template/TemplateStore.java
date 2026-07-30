package com.bionote.template;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for record templates and their ordered fields. */
public interface TemplateStore {
    PageSlice search(
            UUID userId, String scope, String category, String escapedKeyword, int page, int size);

    Optional<TemplateRecord> findVisible(UUID userId, UUID templateId);

    boolean existsByActiveNameKey(String activeNameKey);

    void insert(TemplateMutation template, List<FieldMutation> fields);

    boolean update(
            UUID templateId,
            UUID ownerId,
            long expectedVersion,
            TemplateMutation template,
            List<FieldMutation> fields);

    boolean softDelete(UUID templateId, UUID ownerId, long expectedVersion, Instant deletedAt);

    record PageSlice(List<TemplateRecord> items, long total) {}

    record TemplateRecord(
            UUID id,
            String scope,
            UUID ownerId,
            String name,
            String experimentType,
            String category,
            String description,
            Instant createdAt,
            Instant updatedAt,
            Instant deletedAt,
            long version,
            List<FieldRecord> fields) {}

    record FieldRecord(
            UUID id,
            String fieldKey,
            String label,
            String fieldType,
            boolean required,
            int sortOrder,
            String placeholder,
            String defaultValueJson,
            String optionsJson) {}

    record TemplateMutation(
            UUID id,
            UUID ownerId,
            String name,
            String normalizedName,
            String activeNameKey,
            String experimentType,
            String category,
            String description,
            Instant now) {}

    record FieldMutation(
            UUID id,
            String fieldKey,
            String label,
            String fieldType,
            boolean required,
            int sortOrder,
            String placeholder,
            String defaultValueJson,
            String optionsJson) {}
}
