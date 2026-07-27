package com.bionote.review;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ReviewDtos {
    private ReviewDtos() {}
    public record SubmitRequest(@NotNull UUID reviewerId,@Size(max=2000)String submitNote,@NotNull Long expectedRecordVersion){}
    public record DecisionRequest(@Size(max=3000)String comment){}
    public record Candidate(UUID userId,String displayName,String role){}
    public record ReviewView(UUID id,UUID revisionId,UUID reviewerId,String reviewerName,String status,String decisionComment,Instant assignedAt,Instant decidedAt,boolean canDecide){}
    public record RevisionView(UUID id,int revisionNo,Object snapshot,String contentHash,String submitNote,UUID submittedBy,String submitterName,Instant submittedAt,ReviewView review,List<Map<String,Object>> attachments){}
    public record PendingReview(UUID reviewId,UUID recordId,String recordCode,String recordTitle,UUID projectId,String projectName,int revisionNo,Instant assignedAt){}
}
