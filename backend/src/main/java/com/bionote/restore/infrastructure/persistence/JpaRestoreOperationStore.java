package com.bionote.restore.infrastructure.persistence;

import com.bionote.restore.RestoreDtos;
import com.bionote.restore.RestoreJsonCodec;
import com.bionote.restore.RestoreOperationStore;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
public class JpaRestoreOperationStore implements RestoreOperationStore {
    private final RestoreOperationJpaRepository operations;
    private final RestoreJsonCodec json;

    public JpaRestoreOperationStore(
            RestoreOperationJpaRepository operations, RestoreJsonCodec json) {
        this.operations = operations;
        this.json = json;
    }

    @Override
    public Stored find(UUID recordId, String key) {
        return operations.findStored(recordId.toString(), key).map(this::map).orElse(null);
    }

    @Override
    public void append(OperationRecord value) {
        operations.saveAndFlush(
                new RestoreOperationEntity(
                        value.id(),
                        value.recordId(),
                        value.sourceRevisionId(),
                        value.actorId(),
                        value.beforeVersion(),
                        value.afterVersion(),
                        value.beforeHash(),
                        value.afterHash(),
                        json.encode(value.summary()),
                        value.idempotencyKey(),
                        value.payloadHash(),
                        value.restoredAt()));
    }

    @Override
    public PageSlice list(UUID recordId, int page, int size) {
        var result = operations.listStored(recordId.toString(), PageRequest.of(page, size));
        return new PageSlice(
                result.getContent().stream().map(value -> map(value).summary()).toList(),
                result.getTotalElements());
    }

    private Stored map(RestoreOperationJpaRepository.StoredProjection value) {
        RestoreDtos.OperationSummary summary =
                new RestoreDtos.OperationSummary(
                        uuid(value.getId()),
                        uuid(value.getRecordId()),
                        uuid(value.getSourceRevisionId()),
                        value.getRevisionNo(),
                        uuid(value.getActorId()),
                        value.getActorName(),
                        value.getBeforeVersion(),
                        value.getAfterVersion(),
                        value.getBeforeHash(),
                        value.getAfterHash(),
                        json.decodeSummary(value.getDiffSummaryJson()),
                        value.getRestoredAt());
        return new Stored(summary, value.getPayloadHash());
    }

    private UUID uuid(String value) {
        return UUID.fromString(value);
    }
}
