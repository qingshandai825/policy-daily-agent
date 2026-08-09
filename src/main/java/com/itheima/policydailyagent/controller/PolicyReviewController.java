package com.itheima.policydailyagent.controller;

import com.itheima.policydailyagent.dto.PolicyReviewRequest;
import com.itheima.policydailyagent.dto.PolicyReviewUpdateRequest;
import com.itheima.policydailyagent.dto.PolicySelectionRequest;
import com.itheima.policydailyagent.dto.ReviewTaskSummary;
import com.itheima.policydailyagent.entity.PolicyDocument;
import com.itheima.policydailyagent.service.PolicyReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/review")
public class PolicyReviewController {

    private final PolicyReviewService policyReviewService;

    public PolicyReviewController(PolicyReviewService policyReviewService) {
        this.policyReviewService = policyReviewService;
    }

    @GetMapping("/tasks/{taskId}/policies")
    public ResponseEntity<List<PolicyDocument>> listPoliciesForTask(@PathVariable Long taskId) {
        return ResponseEntity.ok(policyReviewService.listPoliciesForTask(taskId));
    }

    @GetMapping("/tasks/{taskId}/summary")
    public ResponseEntity<ReviewTaskSummary> summarizeTask(@PathVariable Long taskId) {
        return ResponseEntity.ok(policyReviewService.summarizeTask(taskId));
    }

    @PostMapping("/tasks/{taskId}/selection")
    public ResponseEntity<?> updateSelection(
            @PathVariable Long taskId,
            @RequestBody(required = false) PolicySelectionRequest request
    ) {
        try {
            return ResponseEntity.ok(policyReviewService.updateSelection(taskId, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/policies/{policyId}/approve")
    public ResponseEntity<?> approve(
            @PathVariable Long policyId,
            @RequestBody(required = false) PolicyReviewRequest request
    ) {
        try {
            return ResponseEntity.ok(policyReviewService.approve(policyId, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/policies/{policyId}/reject")
    public ResponseEntity<?> reject(
            @PathVariable Long policyId,
            @RequestBody(required = false) PolicyReviewRequest request
    ) {
        try {
            return ResponseEntity.ok(policyReviewService.reject(policyId, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PatchMapping("/policies/{policyId}")
    public ResponseEntity<?> updatePolicy(
            @PathVariable Long policyId,
            @RequestBody PolicyReviewUpdateRequest request
    ) {
        try {
            return ResponseEntity.ok(policyReviewService.updatePolicy(policyId, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
