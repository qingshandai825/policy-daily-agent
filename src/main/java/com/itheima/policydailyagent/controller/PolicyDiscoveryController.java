package com.itheima.policydailyagent.controller;

import com.itheima.policydailyagent.agent.PolicyDailyOrchestrator;
import com.itheima.policydailyagent.agent.dto.AgentCollectionOutcome;
import com.itheima.policydailyagent.dto.PolicyDiscoverRequest;
import com.itheima.policydailyagent.dto.PolicyLinkPreviewResult;
import com.itheima.policydailyagent.service.PolicyDiscoveryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/search")
public class PolicyDiscoveryController {

    private final PolicyDiscoveryService policyDiscoveryService;
    private final PolicyDailyOrchestrator orchestrator;

    public PolicyDiscoveryController(
            PolicyDiscoveryService policyDiscoveryService,
            PolicyDailyOrchestrator orchestrator
    ) {
        this.policyDiscoveryService = policyDiscoveryService;
        this.orchestrator = orchestrator;
    }

    @PostMapping("/discover-and-save")
    public ResponseEntity<?> discoverAndSave(
            @Valid @RequestBody PolicyDiscoverRequest request
    ) {
        try {
            AgentCollectionOutcome outcome = orchestrator.collectPolicies(request);
            return ResponseEntity.ok()
                    .header("X-Agent-Run-Id", String.valueOf(outcome.agentRunId()))
                    .body(outcome.result());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/preview-links")
    public ResponseEntity<?> previewLinks(
            @RequestBody Map<String, String> request
    ) {
        try {
            String listPageUrl = request.get("listPageUrl");
            PolicyLinkPreviewResult result = policyDiscoveryService.previewLinks(listPageUrl);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
        }
    }
}