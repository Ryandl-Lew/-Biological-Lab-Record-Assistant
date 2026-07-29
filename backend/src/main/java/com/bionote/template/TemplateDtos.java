package com.bionote.template;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TemplateDtos {
    private TemplateDtos() {}
    public record FieldRequest(@NotBlank @Size(max=100) String fieldKey,@NotBlank @Size(max=160) String label,
                               @NotBlank String fieldType,boolean required,@Size(max=300) String placeholder,
                               Object defaultValue,List<String> options) {}
    public record SaveRequest(@NotBlank @Size(max=120) String name,@Size(max=100) String experimentType,
                              @Size(max=80) String category,@Size(max=2000) String description,
                              @NotNull @Valid List<FieldRequest> fields,Long version) {}
    public record FieldView(UUID id,String fieldKey,String label,String fieldType,boolean required,int sortOrder,
                            String placeholder,Object defaultValue,List<String> options) {}
    public record View(UUID id,String scope,UUID ownerId,String name,String experimentType,String category,String description,
                       Instant createdAt,Instant updatedAt,long version,List<FieldView> fields,boolean canEdit,boolean canDelete) {}
}

