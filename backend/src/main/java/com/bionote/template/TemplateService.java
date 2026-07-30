package com.bionote.template;

import com.bionote.collaboration.CollaborationEvents;
import com.bionote.common.ApiException;
import com.bionote.common.PagedResponse;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemplateService implements TemplateUseCase {
    private static final Set<String> TYPES =
            Set.of("SINGLE_LINE_TEXT", "MULTI_LINE_TEXT", "NUMBER", "DATE", "SELECT", "FILE");
    private final TemplateStore templates;
    private final TemplateJsonCodec json;
    private final CollaborationEvents events;

    public TemplateService(
            TemplateStore templates, TemplateJsonCodec json, CollaborationEvents events) {
        this.templates = templates;
        this.json = json;
        this.events = events;
    }

    @Override
    public PagedResponse<TemplateDtos.View> list(
            UUID user, String scope, String category, String keyword, int page, int size) {
        page = Math.max(0, page);
        size = Math.max(1, Math.min(100, size));
        String normalizedScope = blankToNull(scope);
        String normalizedCategory = blankToNull(category);
        var result =
                templates.search(
                        user, normalizedScope, normalizedCategory, esc(keyword), page, size);
        return PagedResponse.of(
                result.items().stream().map(template -> view(user, template)).toList(),
                page,
                size,
                result.total());
    }

    @Override
    public TemplateDtos.View get(UUID user, UUID id) {
        return view(user, visible(user, id));
    }

    @Override
    @Transactional
    public TemplateDtos.View create(UUID user, TemplateDtos.SaveRequest request) {
        validate(request);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String normalized = normal(request.name());
        var mutation = mutation(id, user, request, normalized, now);
        try {
            templates.insert(mutation, fields(request.fields()));
        } catch (DataIntegrityViolationException e) {
            throw duplicateName();
        }
        events.audit(
                user,
                null,
                null,
                "TEMPLATE_CREATED",
                "TEMPLATE",
                id,
                Map.of("name", request.name().trim()));
        return get(user, id);
    }

    @Override
    @Transactional
    public TemplateDtos.View update(UUID user, UUID id, TemplateDtos.SaveRequest request) {
        validate(request);
        TemplateStore.TemplateRecord current = owned(user, id);
        if (request.version() == null || request.version() != current.version())
            throw optimisticConflict();
        String normalized = normal(request.name());
        try {
            boolean changed =
                    templates.update(
                            id,
                            user,
                            current.version(),
                            mutation(id, user, request, normalized, Instant.now()),
                            fields(request.fields()));
            if (!changed) throw optimisticConflict();
        } catch (OptimisticLockingFailureException e) {
            throw optimisticConflict();
        } catch (DataIntegrityViolationException e) {
            throw duplicateName();
        }
        events.audit(
                user,
                null,
                null,
                "TEMPLATE_UPDATED",
                "TEMPLATE",
                id,
                Map.of("name", request.name().trim()));
        return get(user, id);
    }

    @Override
    @Transactional
    public void delete(UUID user, UUID id) {
        TemplateStore.TemplateRecord current = owned(user, id);
        if (current.deletedAt() != null) return;
        try {
            if (!templates.softDelete(id, user, current.version(), Instant.now()))
                throw optimisticConflict();
        } catch (OptimisticLockingFailureException e) {
            throw optimisticConflict();
        }
        events.audit(
                user,
                null,
                null,
                "TEMPLATE_DELETED",
                "TEMPLATE",
                id,
                Map.of("name", current.name()));
    }

    @Override
    @Transactional
    public TemplateDtos.View copy(UUID user, UUID source) {
        TemplateDtos.View original = get(user, source);
        String base = original.name() + " 副本";
        String name = base;
        int sequence = 2;
        while (templates.existsByActiveNameKey(user + ":" + normal(name)))
            name = base + " " + sequence++;
        List<TemplateDtos.FieldRequest> copiedFields =
                original.fields().stream()
                        .map(
                                field ->
                                        new TemplateDtos.FieldRequest(
                                                field.fieldKey(),
                                                field.label(),
                                                field.fieldType(),
                                                field.required(),
                                                field.placeholder(),
                                                field.defaultValue(),
                                                field.options()))
                        .toList();
        return create(
                user,
                new TemplateDtos.SaveRequest(
                        name,
                        original.experimentType(),
                        original.category(),
                        original.description(),
                        copiedFields,
                        null));
    }

    private TemplateStore.TemplateMutation mutation(
            UUID id,
            UUID ownerId,
            TemplateDtos.SaveRequest request,
            String normalized,
            Instant now) {
        return new TemplateStore.TemplateMutation(
                id,
                ownerId,
                request.name().trim(),
                normalized,
                ownerId + ":" + normalized,
                trim(request.experimentType()),
                trim(request.category()),
                trim(request.description()),
                now);
    }

    private List<TemplateStore.FieldMutation> fields(List<TemplateDtos.FieldRequest> requested) {
        java.util.ArrayList<TemplateStore.FieldMutation> result = new java.util.ArrayList<>();
        for (int index = 0; index < requested.size(); index++) {
            var field = requested.get(index);
            List<String> options =
                    "SELECT".equals(field.fieldType())
                            ? field.options().stream()
                                    .map(String::trim)
                                    .filter(value -> !value.isBlank())
                                    .distinct()
                                    .toList()
                            : List.of();
            result.add(
                    new TemplateStore.FieldMutation(
                            UUID.randomUUID(),
                            field.fieldKey().trim(),
                            field.label().trim(),
                            field.fieldType(),
                            field.required(),
                            index,
                            trim(field.placeholder()),
                            json.encode(field.defaultValue()),
                            options.isEmpty() ? null : json.encode(options)));
        }
        return result;
    }

    private void validate(TemplateDtos.SaveRequest request) {
        Set<String> keys = new HashSet<>();
        Set<String> labels = new HashSet<>();
        for (var field : request.fields()) {
            String key = field.fieldKey().trim();
            if (!keys.add(key)) throw validation("fields", "字段 key 不能重复");
            if (!labels.add(field.label().trim().toLowerCase(Locale.ROOT)))
                throw validation("fields", "字段名称不能重复");
            if (!TYPES.contains(field.fieldType()))
                throw validation("fields", "不支持的字段类型: " + field.fieldType());
            if ("FILE".equals(field.fieldType()) && field.defaultValue() != null)
                throw validation("fields", "文件字段不能设置默认值");
            if ("SELECT".equals(field.fieldType())) {
                List<String> options =
                        field.options() == null
                                ? List.of()
                                : field.options().stream()
                                        .map(String::trim)
                                        .filter(value -> !value.isBlank())
                                        .distinct()
                                        .toList();
                if (options.isEmpty()) throw validation("fields", "下拉字段至少需要一个选项");
                if (field.defaultValue() != null
                        && !options.contains(field.defaultValue().toString()))
                    throw validation("fields", "下拉默认值必须来自选项");
            }
        }
    }

    private TemplateDtos.View view(UUID user, TemplateStore.TemplateRecord template) {
        List<TemplateDtos.FieldView> fields =
                template.fields().stream()
                        .map(
                                field ->
                                        new TemplateDtos.FieldView(
                                                field.id(),
                                                field.fieldKey(),
                                                field.label(),
                                                field.fieldType(),
                                                field.required(),
                                                field.sortOrder(),
                                                field.placeholder(),
                                                json.decodeValue(field.defaultValueJson()),
                                                json.decodeOptions(field.optionsJson())))
                        .toList();
        boolean editable = user.equals(template.ownerId());
        return new TemplateDtos.View(
                template.id(),
                template.scope(),
                template.ownerId(),
                template.name(),
                template.experimentType(),
                template.category(),
                template.description(),
                template.createdAt(),
                template.updatedAt(),
                template.version(),
                fields,
                editable,
                editable);
    }

    private TemplateStore.TemplateRecord visible(UUID user, UUID id) {
        return templates
                .findVisible(user, id)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "模板不存在或无权访问"));
    }

    private TemplateStore.TemplateRecord owned(UUID user, UUID id) {
        TemplateStore.TemplateRecord template = visible(user, id);
        if (!"PERSONAL".equals(template.scope()) || !user.equals(template.ownerId()))
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "系统模板或他人模板不可修改");
        return template;
    }

    private ApiException duplicateName() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "DUPLICATE_RESOURCE",
                "个人模板名称已存在",
                Map.of("name", "个人模板名称已存在"));
    }

    private ApiException optimisticConflict() {
        return new ApiException(HttpStatus.CONFLICT, "OPTIMISTIC_LOCK_CONFLICT", "模板已被其他页面更新");
    }

    private ApiException validation(String field, String message) {
        return new ApiException(
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "请检查模板字段", Map.of(field, message));
    }

    private String normal(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String esc(String value) {
        return value == null
                ? ""
                : normal(value).replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
