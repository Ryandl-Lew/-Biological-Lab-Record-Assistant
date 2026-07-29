package com.bionote.record.infrastructure.persistence;

import com.bionote.record.RecordStore;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaRecordStore implements RecordStore {
    private final ExperimentRecordJpaRepository records;
    public JpaRecordStore(ExperimentRecordJpaRepository records) { this.records=records; }

    @Override public void insert(NewRecord record) {
        records.saveAndFlush(new ExperimentRecordEntity(record.id(),record.code(),record.projectId(),record.creatorId(),
                record.title(),record.experimentType(),record.experimentDate(),record.purpose(),record.provisional(),
                record.templateSnapshotJson(),record.fieldValuesJson(),record.contentJson(),record.contentHtmlSanitized(),
                record.contentPlainText(),record.now()));
    }
    @Override public Optional<RecordData> findActive(UUID recordId) {
        return records.findById(recordId).filter(record -> record.deletedAt==null).map(this::map);
    }
    @Override public Optional<RecordData> findActiveForUpdate(UUID recordId) {
        return records.findActiveByIdForUpdate(recordId).map(this::map);
    }
    @Override public PageSlice searchVisible(UUID userId, UUID projectId, UUID creatorId, String status,
                                             String escapedKeyword, int page, int size) {
        var result=records.searchVisible(userId.toString(),projectId==null?null:projectId.toString(),
                creatorId==null?null:creatorId.toString(),status,escapedKeyword,PageRequest.of(page,size));
        return new PageSlice(result.getContent().stream().map(row -> UUID.fromString(row.getId())).toList(),result.getTotalElements());
    }
    @Override public boolean updateWorkingCopy(UUID recordId,long expectedVersion,UpdateRecord update) {
        var record=records.findById(recordId).orElse(null);
        if(record==null||record.deletedAt!=null||record.version!=expectedVersion)return false;
        record.updateWorkingCopy(update.title(),update.experimentType(),update.experimentDate(),update.purpose(),
                update.fieldValuesJson(),update.contentJson(),update.contentHtmlSanitized(),update.contentPlainText(),
                update.provisional(),update.now());
        records.saveAndFlush(record);return true;
    }
    @Override public boolean softDelete(UUID recordId,long expectedVersion,Instant now) {
        var record=records.findById(recordId).orElse(null);
        if(record==null||record.deletedAt!=null||record.version!=expectedVersion)return false;
        record.softDelete(now);records.saveAndFlush(record);return true;
    }
    @Override public void replaceFieldValuesWithoutVersion(UUID recordId,String fieldValuesJson) {
        records.replaceFieldValuesWithoutVersion(recordId.toString(),fieldValuesJson);
    }
    @Override public boolean markSubmitted(UUID recordId,long expectedVersion,int revisionNo,UUID reviewId,Instant now) {
        return records.markSubmitted(recordId.toString(),expectedVersion,revisionNo,reviewId.toString(),now)==1;
    }
    @Override public boolean markReviewApproved(UUID recordId,UUID reviewId,UUID revisionId,Instant now) {
        return records.markReviewApproved(recordId.toString(),reviewId.toString(),revisionId.toString(),now)==1;
    }
    @Override public boolean markReviewChangesRequested(UUID recordId,UUID reviewId,Instant now) {
        return records.markReviewChangesRequested(recordId.toString(),reviewId.toString(),now)==1;
    }
    @Override public boolean restoreWorkingCopy(UUID recordId,long expectedVersion,RestoreRecord value,Instant now) {
        return records.restoreWorkingCopy(recordId.toString(),expectedVersion,value.title(),value.experimentType(),
                value.experimentDate(),value.purpose(),value.templateSnapshotJson(),value.fieldValuesJson(),
                value.contentJson(),value.contentHtmlSanitized(),value.contentPlainText(),now)==1;
    }
    @Override public boolean deleteProvisional(UUID recordId,UUID creatorId) { return records.deleteProvisional(recordId,creatorId)==1; }

    private RecordData map(ExperimentRecordEntity r) {
        return new RecordData(r.id,r.code,r.projectId,r.creatorId,r.title,r.experimentType,r.experimentDate,r.purpose,
                r.status,r.provisional,r.templateSnapshotJson,r.fieldValuesJson,r.contentJson,r.contentHtmlSanitized,
                r.contentPlainText,r.currentRevisionNo,r.currentReviewId,r.finalRevisionId,r.version,r.createdAt,r.updatedAt,r.deletedAt);
    }
}
