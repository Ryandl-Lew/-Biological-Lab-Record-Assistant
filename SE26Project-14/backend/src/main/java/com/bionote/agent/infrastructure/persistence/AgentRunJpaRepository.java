package com.bionote.agent.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AgentRunJpaRepository extends JpaRepository<AgentRunEntity, UUID> {
    interface ClaimCandidate {
        String getId();

        long getVersion();
    }

    Optional<AgentRunEntity> findByRequestedByAndIdempotencyKey(
            UUID requestedBy, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AgentRunEntity r where r.id=:id")
    Optional<AgentRunEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query(
            value =
                    "SELECT id,version FROM agent_runs WHERE status='QUEUED' ORDER BY created_at,id LIMIT 10",
            nativeQuery = true)
    List<ClaimCandidate> findClaimCandidates();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    "UPDATE agent_runs SET status='RUNNING',started_at=:now,version=version+1 WHERE id=:id AND status='QUEUED' AND version=:version",
            nativeQuery = true)
    int claim(@Param("id") String id, @Param("version") long version, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    "UPDATE agent_runs SET status='CANCELLED',cancel_requested_at=:now,finished_at=:now,version=version+1 WHERE id=:id AND status='QUEUED'",
            nativeQuery = true)
    int cancelQueued(@Param("id") String id, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    "UPDATE agent_runs SET cancel_requested_at=COALESCE(cancel_requested_at,:now),version=version+1 WHERE id=:id AND status='RUNNING'",
            nativeQuery = true)
    int requestCancelRunning(@Param("id") String id, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    "UPDATE agent_runs SET tool_call_count=tool_call_count+:count WHERE id=:id AND status='RUNNING'",
            nativeQuery = true)
    int incrementToolCalls(@Param("id") String id, @Param("count") int count);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    "UPDATE agent_runs SET status=:status,error_code=:code,error_message=:message,finished_at=:now,version=version+1 WHERE id=:id AND status='RUNNING'",
            nativeQuery = true)
    int finishRunning(
            @Param("id") String id,
            @Param("status") String status,
            @Param("code") String code,
            @Param("message") String message,
            @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    "UPDATE agent_runs SET status='SUCCEEDED',finished_at=:now,version=version+1 WHERE id=:id AND status='RUNNING' AND cancel_requested_at IS NULL",
            nativeQuery = true)
    int markSucceeded(@Param("id") String id, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    "UPDATE agent_runs SET status='FAILED',error_code='WORKER_INTERRUPTED',error_message='Worker stopped before the run completed',finished_at=:now,version=version+1 WHERE status='RUNNING' AND started_at<:cutoff",
            nativeQuery = true)
    int failStaleRunning(@Param("cutoff") Instant cutoff, @Param("now") Instant now);

    @Query(value = "SELECT step_count FROM agent_runs WHERE id=:id FOR UPDATE", nativeQuery = true)
    Integer lockStepCount(@Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    "UPDATE agent_runs SET step_count=:stepNo,input_tokens=input_tokens+:inputTokens,output_tokens=output_tokens+:outputTokens WHERE id=:id",
            nativeQuery = true)
    int updateStepMetrics(
            @Param("id") String id,
            @Param("stepNo") int stepNo,
            @Param("inputTokens") long inputTokens,
            @Param("outputTokens") long outputTokens);

    @Query(
            "select count(r) from AgentRunEntity r where r.requestedBy=:requester and r.status in ('QUEUED','RUNNING')")
    long countActiveForRequester(@Param("requester") UUID requester);

    @Query(
            "select count(r) from AgentRunEntity r where r.projectId=:projectId and r.status in ('QUEUED','RUNNING')")
    long countActiveForProject(@Param("projectId") UUID projectId);

    long countBySubjectIdAndArtifactKindAndPayloadHashAndCreatedAtGreaterThanEqual(
            UUID subjectId, String artifactKind, String payloadHash, Instant cutoff);
}
