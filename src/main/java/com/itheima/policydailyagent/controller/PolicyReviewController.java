package com.itheima.policydailyagent.controller;

import com.itheima.policydailyagent.domain.policy.PolicyReviewStatus;
import com.itheima.policydailyagent.dto.PolicyReviewRequest;
import com.itheima.policydailyagent.dto.PolicyReviewUpdateRequest;
import com.itheima.policydailyagent.service.PolicyCandidateQueryService;
import com.itheima.policydailyagent.service.PolicyReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/review")
public class PolicyReviewController {

    private final PolicyReviewService policyReviewService;
    private final PolicyCandidateQueryService candidateQueryService;

    public PolicyReviewController(
            PolicyReviewService policyReviewService,
            PolicyCandidateQueryService candidateQueryService
    ) {
        this.policyReviewService = policyReviewService;
        this.candidateQueryService = candidateQueryService;
    }

    @GetMapping("/search-tasks/{taskId}/policies")
    public ResponseEntity<?> listPoliciesForSearchTask(
            @PathVariable Long taskId,
            @RequestParam(required = false) PolicyReviewStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sourceId
    ) {
        return execute(() -> candidateQueryService.list(taskId, status, keyword, sourceId));
    }

    @GetMapping("/search-tasks/{taskId}/policies/{policyId}")
    public ResponseEntity<?> getPolicyForSearchTask(
            @PathVariable Long taskId,
            @PathVariable Long policyId
    ) {
        return execute(() -> candidateQueryService.get(taskId, policyId));
    }

    @GetMapping("/search-tasks/{taskId}/summary")
    public ResponseEntity<?> summarizeSearchTask(@PathVariable Long taskId) {
        return execute(() -> candidateQueryService.summarize(taskId));
    }

    @GetMapping("/policies/{policyId}/history")
    public ResponseEntity<?> reviewHistory(@PathVariable Long policyId) {
        return execute(() -> policyReviewService.listReviewHistory(policyId));
    }

    @PostMapping("/policies/{policyId}/accept")
    public ResponseEntity<?> accept(
            @PathVariable Long policyId,
            @RequestBody PolicyReviewRequest request
    ) {
        return execute(() -> policyReviewService.accept(policyId, request));
    }

    @PostMapping("/policies/{policyId}/reject")
    public ResponseEntity<?> reject(
            @PathVariable Long policyId,
            @RequestBody PolicyReviewRequest request
    ) {
        return execute(() -> policyReviewService.reject(policyId, request));
    }

    @PostMapping("/policies/{policyId}/defer")
    public ResponseEntity<?> defer(
            @PathVariable Long policyId,
            @RequestBody PolicyReviewRequest request
    ) {
        return execute(() -> policyReviewService.defer(policyId, request));
    }

    @PostMapping("/policies/{policyId}/pending")
    public ResponseEntity<?> resetToPending(
            @PathVariable Long policyId,
            @RequestBody PolicyReviewRequest request
    ) {
        return execute(() -> policyReviewService.resetToPending(policyId, request));
    }

    @PatchMapping("/policies/{policyId}")
    public ResponseEntity<?> updatePolicy(
            @PathVariable Long policyId,
            @RequestBody PolicyReviewUpdateRequest request
    ) {
        return execute(() -> policyReviewService.updatePolicy(policyId, request));
    }

    private ResponseEntity<?> execute(Supplier<?> action) {
        try {
            return ResponseEntity.ok(action.get());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
