package com.itheima.policydailyagent.controller;

import com.itheima.policydailyagent.dto.PolicyAnalysisRequest;
import com.itheima.policydailyagent.service.AgentUnavailableException;
import com.itheima.policydailyagent.service.PolicyAnalysisExecutionException;
import com.itheima.policydailyagent.service.PolicyAnalysisWorkflowService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai/policies")
public class PolicySummaryController {

    private final PolicyAnalysisWorkflowService analysisWorkflowService;

    public PolicySummaryController(PolicyAnalysisWorkflowService analysisWorkflowService) {
        this.analysisWorkflowService = analysisWorkflowService;
    }

    @PostMapping("/{id}/summary")
    public ResponseEntity<?> summarize(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(
                    analysisWorkflowService.analyze(id, new PolicyAnalysisRequest("legacy-summary-api"))
            );
        } catch (AgentUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (PolicyAnalysisExecutionException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("message", e.getMessage()));
        }
    }
}