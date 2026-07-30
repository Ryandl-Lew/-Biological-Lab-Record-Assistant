package com.bionote.config.infrastructure.persistence;

import com.bionote.config.DemoDataStore;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaDemoDataStore implements DemoDataStore {
    @PersistenceContext private EntityManager entityManager;

    @Override
    public Optional<UUID> findProjectIdByName(String name) {
        return firstUuid("SELECT id FROM projects WHERE name=:name", "name", name);
    }

    @Override
    public Optional<UUID> findUserIdByEmail(String normalizedEmail) {
        return firstUuid(
                "SELECT id FROM users WHERE email_normalized=:email", "email", normalizedEmail);
    }

    @Override
    public Optional<String> findEmailByUserId(UUID userId) {
        List<?> values =
                entityManager
                        .createNativeQuery("SELECT email_normalized FROM users WHERE id=:id")
                        .setParameter("id", userId.toString())
                        .setMaxResults(1)
                        .getResultList();
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0).toString());
    }

    @Override
    public Optional<UUID> findChangesRequestedRevisionOneRecordByTitlePrefix(String titlePrefix) {
        List<?> values =
                entityManager
                        .createNativeQuery(
                                """
                        SELECT id FROM experiment_records
                        WHERE title LIKE :title AND current_revision_no=1 AND status='CHANGES_REQUESTED'
                        """)
                        .setParameter("title", titlePrefix + "%")
                        .setMaxResults(1)
                        .getResultList();
        return values.isEmpty() ? Optional.empty() : Optional.of(uuid(values.get(0)));
    }

    @Override
    public Optional<UUID> findRecordCreatorId(UUID recordId) {
        return firstUuid(
                "SELECT creator_id FROM experiment_records WHERE id=:id",
                "id",
                recordId.toString());
    }

    @Override
    public boolean activeAttachmentExists(UUID recordId, String originalFilename) {
        Number count =
                (Number)
                        entityManager
                                .createNativeQuery(
                                        """
                        SELECT COUNT(*) FROM attachments
                        WHERE record_id=:recordId AND original_filename=:filename AND deleted_at IS NULL
                        """)
                                .setParameter("recordId", recordId.toString())
                                .setParameter("filename", originalFilename)
                                .getSingleResult();
        return count.longValue() > 0;
    }

    @Override
    @Transactional
    public void alignRecordTimes(
            UUID recordId, Instant createdAt, Instant updatedAt, Instant attachmentCreatedAt) {
        entityManager
                .createNativeQuery(
                        "UPDATE experiment_records SET created_at=:createdAt,updated_at=:updatedAt WHERE id=:id")
                .setParameter("createdAt", Timestamp.from(createdAt))
                .setParameter("updatedAt", Timestamp.from(updatedAt))
                .setParameter("id", recordId.toString())
                .executeUpdate();
        entityManager
                .createNativeQuery(
                        "UPDATE attachments SET created_at=:createdAt WHERE record_id=:recordId")
                .setParameter("createdAt", Timestamp.from(attachmentCreatedAt))
                .setParameter("recordId", recordId.toString())
                .executeUpdate();
    }

    @Override
    @Transactional
    public void spreadProjectTimeline(UUID projectId, Instant start, long stepHours) {
        List<?> eventIds =
                entityManager
                        .createNativeQuery(
                                """
                        SELECT id FROM audit_events WHERE project_id=:projectId ORDER BY created_at,id
                        """)
                        .setParameter("projectId", projectId.toString())
                        .getResultList();
        Instant last = start;
        for (int i = 0; i < eventIds.size(); i++) {
            last = start.plus(i * stepHours, ChronoUnit.HOURS);
            entityManager
                    .createNativeQuery("UPDATE audit_events SET created_at=:createdAt WHERE id=:id")
                    .setParameter("createdAt", Timestamp.from(last))
                    .setParameter("id", eventIds.get(i).toString())
                    .executeUpdate();
        }
        entityManager
                .createNativeQuery(
                        "UPDATE projects SET created_at=:createdAt,updated_at=:updatedAt WHERE id=:id")
                .setParameter("createdAt", Timestamp.from(start))
                .setParameter("updatedAt", Timestamp.from(last))
                .setParameter("id", projectId.toString())
                .executeUpdate();
    }

    private Optional<UUID> firstUuid(String sql, String parameterName, Object parameterValue) {
        List<?> values =
                entityManager
                        .createNativeQuery(sql)
                        .setParameter(parameterName, parameterValue)
                        .setMaxResults(1)
                        .getResultList();
        return values.isEmpty() ? Optional.empty() : Optional.of(uuid(values.get(0)));
    }

    private UUID uuid(Object value) {
        return value instanceof UUID id ? id : UUID.fromString(value.toString());
    }
}
