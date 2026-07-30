package com.bionote.restore;

import com.bionote.common.ApiException;
import com.bionote.common.PagedResponse;
import com.bionote.project.ProjectMemberStore;
import com.bionote.record.RecordStore;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RestoreHistoryService implements RestoreHistoryUseCase {
    private final RestoreOperationStore operations;
    private final RecordStore records;
    private final ProjectMemberStore members;

    public RestoreHistoryService(
            RestoreOperationStore operations, RecordStore records, ProjectMemberStore members) {
        this.operations = operations;
        this.records = records;
        this.members = members;
    }

    @Override
    public PagedResponse<RestoreDtos.OperationSummary> list(
            UUID actorId, UUID recordId, int page, int size) {
        var record =
                records.findActive(recordId)
                        .filter(value -> members.exists(value.projectId(), actorId))
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "RESOURCE_NOT_FOUND",
                                                "记录不存在或无权访问"));
        page = Math.max(0, page);
        size = Math.max(1, Math.min(100, size));
        var result = operations.list(record.id(), page, size);
        return PagedResponse.of(result.items(), page, size, result.total());
    }
}
