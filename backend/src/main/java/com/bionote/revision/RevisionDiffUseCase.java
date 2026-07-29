package com.bionote.revision;

import java.util.UUID;

public interface RevisionDiffUseCase {
    RevisionDtos.DiffResult compare(UUID actorId, UUID recordId, UUID fromRevisionId, UUID toRevisionId,
                                    String to, boolean includeUnchanged);
    RevisionDtos.DiffResult compareWorkingCopyToRevision(UUID actorId,UUID recordId,UUID sourceRevisionId,
                                                         boolean includeUnchanged);
}
