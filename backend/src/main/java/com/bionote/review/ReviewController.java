package com.bionote.review;

import com.bionote.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api/v1")
public class ReviewController {
    private final ReviewService service;public ReviewController(ReviewService service){this.service=service;}private UUID current(Authentication a){return UUID.fromString(a.getName());}
    @GetMapping("/records/{recordId}/reviewer-candidates") ApiResponse<List<ReviewDtos.Candidate>> candidates(Authentication a,@PathVariable UUID recordId){return ApiResponse.of(service.candidates(current(a),recordId));}
    @PostMapping("/records/{recordId}/submissions") ApiResponse<ReviewDtos.RevisionView> submit(Authentication a,@PathVariable UUID recordId,@Valid @RequestBody ReviewDtos.SubmitRequest r,@RequestHeader(value="Idempotency-Key",required=false)String key){return ApiResponse.of(service.submit(current(a),recordId,r,key));}
    @PostMapping("/records/{recordId}/reviews/{reviewId}/request-changes") ApiResponse<ReviewDtos.RevisionView> requestChanges(Authentication a,@PathVariable UUID recordId,@PathVariable UUID reviewId,@RequestBody(required=false)ReviewDtos.DecisionRequest r){return ApiResponse.of(service.requestChanges(current(a),recordId,reviewId,r==null?null:r.comment()));}
    @PostMapping("/records/{recordId}/reviews/{reviewId}/approve") ApiResponse<ReviewDtos.RevisionView> approve(Authentication a,@PathVariable UUID recordId,@PathVariable UUID reviewId,@RequestBody(required=false)ReviewDtos.DecisionRequest r){return ApiResponse.of(service.approve(current(a),recordId,reviewId,r==null?null:r.comment()));}
    @GetMapping("/reviews/pending") ApiResponse<List<ReviewDtos.PendingReview>> pending(Authentication a){return ApiResponse.of(service.pending(current(a)));}
}
