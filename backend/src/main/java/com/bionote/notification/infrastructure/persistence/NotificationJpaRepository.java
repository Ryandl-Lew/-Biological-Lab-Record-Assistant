package com.bionote.notification.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface NotificationJpaRepository extends JpaRepository<NotificationEntity, UUID> {
    Page<NotificationEntity> findByRecipientIdOrderByCreatedAtDescIdDesc(
            UUID recipientId, Pageable pageable);

    Page<NotificationEntity> findByRecipientIdAndReadAtIsNullOrderByCreatedAtDescIdDesc(
            UUID recipientId, Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(UUID recipientId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "update NotificationEntity n set n.readAt=coalesce(n.readAt,:now) where n.id=:id and n.recipientId=:recipientId")
    int markRead(
            @Param("id") UUID id,
            @Param("recipientId") UUID recipientId,
            @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "update NotificationEntity n set n.readAt=:now where n.recipientId=:recipientId and n.readAt is null")
    int markAllRead(@Param("recipientId") UUID recipientId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    """
            INSERT INTO notifications(id,recipient_id,type,title,body,payload_json,dedup_key,created_at)
            VALUES(:id,:recipientId,:type,:title,:body,:payload,:dedupKey,:createdAt)
            ON DUPLICATE KEY UPDATE dedup_key=dedup_key
            """,
            nativeQuery = true)
    int appendIfAbsent(
            @Param("id") String id,
            @Param("recipientId") String recipientId,
            @Param("type") String type,
            @Param("title") String title,
            @Param("body") String body,
            @Param("payload") String payload,
            @Param("dedupKey") String dedupKey,
            @Param("createdAt") Instant createdAt);
}
