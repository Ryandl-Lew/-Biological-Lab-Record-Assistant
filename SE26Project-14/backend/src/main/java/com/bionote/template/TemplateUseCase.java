package com.bionote.template;

import com.bionote.common.PagedResponse;
import java.util.UUID;

/** HTTP-facing template application boundary. */
public interface TemplateUseCase {
    PagedResponse<TemplateDtos.View> list(
            UUID userId, String scope, String category, String keyword, int page, int size);

    TemplateDtos.View get(UUID userId, UUID id);

    TemplateDtos.View create(UUID userId, TemplateDtos.SaveRequest request);

    TemplateDtos.View update(UUID userId, UUID id, TemplateDtos.SaveRequest request);

    void delete(UUID userId, UUID id);

    TemplateDtos.View copy(UUID userId, UUID sourceId);
}
