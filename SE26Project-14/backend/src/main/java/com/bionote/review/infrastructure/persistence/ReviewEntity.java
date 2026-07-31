package com.bionote.review.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "reviews")
class ReviewEntity {
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID id;

    @Column(name = "record_id", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID recordId;

    @Column(name = "revision_id", nullable = false, unique = true)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID revisionId;

    @Column(name = "reviewer_id", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID reviewerId;

    @Column(nullable = false, length = 30)
    String status;

    @Column(name = "decision_comment", length = 3000)
    String decisionComment;

    @Column(name = "assigned_at", nullable = false)
    Instant assignedAt;

    @Column(name = "decided_at")
    Instant decidedAt;

    protected ReviewEntity() {}

    ReviewEntity(
            UUID id,
            UUID recordId,
            UUID revisionId,
            UUID reviewerId,
            String status,
            String decisionComment,
            Instant assignedAt,
            Instant decidedAt) {
        this.id = id;
        this.recordId = recordId;
        this.revisionId = revisionId;
        this.reviewerId = reviewerId;
        this.status = status;
        this.decisionComment = decisionComment;
        this.assignedAt = assignedAt;
        this.decidedAt = decidedAt;
    }
}
