package com.bionote.template.infrastructure.persistence;

import com.bionote.template.TemplateStore;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaTemplateStore implements TemplateStore {
    private final RecordTemplateJpaRepository templates;
    private final TemplateFieldJpaRepository fields;

    public JpaTemplateStore(RecordTemplateJpaRepository templates, TemplateFieldJpaRepository fields) {
        this.templates = templates;
        this.fields = fields;
    }

    @Override public PageSlice search(UUID userId, String scope, String category, String escapedKeyword, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("scope"), Sort.Order.desc("updatedAt"), Sort.Order.desc("id")));
        var result = templates.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            predicates.add(cb.or(cb.equal(root.get("scope"), "SYSTEM"), cb.equal(root.get("ownerId"), userId)));
            if (scope != null) predicates.add(cb.equal(root.get("scope"), scope));
            if (category != null) predicates.add(cb.equal(root.get("category"), category));
            String pattern = "%" + escapedKeyword.toLowerCase(Locale.ROOT) + "%";
            predicates.add(cb.or(
                    cb.like(cb.lower(root.get("name")), pattern, '!'),
                    cb.like(cb.lower(cb.coalesce(root.get("description"), "")), pattern, '!')));
            return cb.and(predicates.toArray(Predicate[]::new));
        }, pageable);
        return new PageSlice(result.getContent().stream().map(this::map).toList(), result.getTotalElements());
    }

    @Override public Optional<TemplateRecord> findVisible(UUID userId, UUID templateId) {
        return templates.findById(templateId)
                .filter(entity -> entity.deletedAt == null)
                .filter(entity -> "SYSTEM".equals(entity.scope) || userId.equals(entity.ownerId))
                .map(this::map);
    }

    @Override public boolean existsByActiveNameKey(String activeNameKey) {
        return templates.existsByActiveNameKey(activeNameKey);
    }

    @Override public void insert(TemplateMutation template, List<FieldMutation> fieldMutations) {
        var entity = new RecordTemplateEntity(template.id(), template.ownerId(), template.name(), template.normalizedName(),
                template.activeNameKey(), template.experimentType(), template.category(), template.description(), template.now());
        templates.saveAndFlush(entity);
        fields.saveAll(fieldMutations.stream().map(field -> entity(template.id(), field)).toList());
        fields.flush();
    }

    @Override public boolean update(UUID templateId, UUID ownerId, long expectedVersion, TemplateMutation mutation,
                                    List<FieldMutation> fieldMutations) {
        var entity = templates.findById(templateId).orElse(null);
        if (entity == null || entity.deletedAt != null || !"PERSONAL".equals(entity.scope)
                || !ownerId.equals(entity.ownerId) || entity.version != expectedVersion) return false;
        entity.update(mutation.name(), mutation.normalizedName(), mutation.activeNameKey(), mutation.experimentType(),
                mutation.category(), mutation.description(), mutation.now());
        fields.deleteByTemplateId(templateId);
        fields.saveAll(fieldMutations.stream().map(field -> entity(templateId, field)).toList());
        templates.saveAndFlush(entity);
        fields.flush();
        return true;
    }

    @Override public boolean softDelete(UUID templateId, UUID ownerId, long expectedVersion, java.time.Instant deletedAt) {
        var entity = templates.findById(templateId).orElse(null);
        if (entity == null || entity.deletedAt != null || !ownerId.equals(entity.ownerId) || entity.version != expectedVersion) return false;
        entity.softDelete(deletedAt);
        templates.saveAndFlush(entity);
        return true;
    }

    private TemplateFieldEntity entity(UUID templateId, FieldMutation field) {
        return new TemplateFieldEntity(field.id(), templateId, field.fieldKey(), field.label(), field.fieldType(),
                field.required(), field.sortOrder(), field.placeholder(), field.defaultValueJson(), field.optionsJson());
    }

    private TemplateRecord map(RecordTemplateEntity entity) {
        List<FieldRecord> fieldRecords = fields.findByTemplateIdOrderBySortOrderAsc(entity.id).stream()
                .map(field -> new FieldRecord(field.id, field.fieldKey, field.label, field.fieldType, field.required,
                        field.sortOrder, field.placeholder, field.defaultValueJson, field.optionsJson))
                .toList();
        return new TemplateRecord(entity.id, entity.scope, entity.ownerId, entity.name, entity.experimentType,
                entity.category, entity.description, entity.createdAt, entity.updatedAt, entity.deletedAt,
                entity.version, fieldRecords);
    }
}
