package com.bionote.domain.record;

import java.util.UUID;

public record RecordContext(
        UUID recordId,
        UUID projectId,
        UUID creatorId,
        String recordStatus,
        String projectStatus,
        boolean deleted,
        boolean provisional,
        String actorProjectRole) {}
