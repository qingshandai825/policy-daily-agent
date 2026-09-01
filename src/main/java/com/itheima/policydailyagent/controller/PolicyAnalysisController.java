package com.itheima.policydailyagent.controller;

import com.itheima.policydailyagent.dto.PolicyAnalysisConfirmRequest;
import com.itheima.policydailyagent.dto.PolicyAnalysisRequest;
import com.itheima.policydailyagent.service.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class PolicyAnalysisController {

    private final AgentAvailabilityService availabilityService;
    private final PolicyAnalysisWorkflowService analysisWorkflowService;
    private final PolicyContentRefreshService contentRefreshService;
    private final ReportContentPoolService contentPoolService;

    public PolicyAnalysisController(
            AgentAvailabilityService availabilityService,
            PolicyAnalysisWorkflowService analysisWorkflowService,
            PolicyContentRefreshService contentRefreshService,
            ReportContentPoolService contentPoolService
    ) {
        this.availabilityService = availabilityService;
        this.analysisWorkflowService = analysisWorkflowService;
        this.contentRefreshService = contentRefreshService;
        this.contentPoolService = contentPoolService;
    }

    @GetMapping("/agent/status")
    public ResponseEntity<?> agentStatus() {
        return ResponseEntity.ok(availabilityService.status());
    }

    @GetMapping("/accepted-policies")
    public ResponseEntity<?> listAccepted() {
        return ResponseEntity.ok(analysisWorkflowService.listAccepted());
    }

    @GetMapping("/accepted-policies/sections")
    public ResponseEntity<?> listEligibleSections() {
        return ResponseEntity.ok(analysisWorkflowService.listEligibleSections());
    }

    @GetMapping("/accepted-policies/{policyId}")
    public ResponseEntity<?> getAccepted(@PathVariable Long policyId) {
        try {
            return ResponseEntity.ok(analysisWorkflowService.getAccepted(policyId));
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    @PostMapping("/accepted-policies/{policyId}/refresh-content")
    public ResponseEntity<?> refreshContent(@PathVariable Long policyId) {
        try {
            contentRefreshService.refreshAccepted(policyId);
            return ResponseEntity.ok(analysisWorkflowService.getAccepted(policyId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/accepted-policies/{policyId}/analyses")
    public ResponseEntity<?> listAnalyses(@PathVariable Long policyId) {
        try {
            return ResponseEntity.ok(analysisWorkflowService.listAnalyses(policyId));
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    @PostMapping("/accepted-policies/{policyId}/analyses")
    public ResponseEntity<?> analyze(
            @PathVariable Long policyId,
            @Valid @RequestBody PolicyAnalysisRequest request
    ) {
        try {
            return ResponseEntity.ok(analysisWorkflowService.analyze(policyId, request));
        } catch (AgentUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        } catch (PolicyAnalysisExecutionException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/accepted-policies/{policyId}/analyses/{analysisId}/confirm")
    public ResponseEntity<?> confirm(
            @PathVariable Long policyId,
            @PathVariable Long analysisId,
            @Valid @RequestBody PolicyAnalysisConfirmRequest request
    ) {
        try {
            return ResponseEntity.ok(contentPoolService.confirm(policyId, analysisId, request));
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    private ResponseEntity<?> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
}
