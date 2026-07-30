package com.bionote.revision.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RecordRevisionJpaRepository extends JpaRepository<RecordRevisionEntity, UUID> {
    Optional<RecordRevisionEntity> findByRecordIdAndIdempotencyKey(
            UUID recordId, String idempotencyKey);

    List<RecordRevisionEntity> findByRecordIdOrderByRevisionNoAsc(UUID recordId);
}
