package com.bionote.record.infrastructure.persistence;

import com.bionote.record.RecordRevisionDisplayReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaRecordRevisionDisplayReader implements RecordRevisionDisplayReader {
    @PersistenceContext private EntityManager entityManager;

    @Override public Optional<DisplayedRevision> findCompleted(UUID finalRevisionId) {
        if(finalRevisionId==null)return Optional.empty();
        return revision(finalRevisionId);
    }
    @Override public Optional<DisplayedRevision> findInReview(UUID currentReviewId) {
        if(currentReviewId==null)return Optional.empty();
        List<?> ids=entityManager.createNativeQuery("SELECT revision_id FROM reviews WHERE id=:id")
                .setParameter("id",currentReviewId.toString()).getResultList();
        return ids.isEmpty()?Optional.empty():revision(UUID.fromString(ids.get(0).toString()));
    }
    private Optional<DisplayedRevision> revision(UUID id) {
        List<?> rows=entityManager.createNativeQuery("SELECT id,revision_no,snapshot_json FROM record_revisions WHERE id=:id")
                .setParameter("id",id.toString()).getResultList();
        if(rows.isEmpty())return Optional.empty();Object[] row=(Object[])rows.get(0);
        return Optional.of(new DisplayedRevision(UUID.fromString(row[0].toString()),((Number)row[1]).intValue(),row[2].toString()));
    }
}
