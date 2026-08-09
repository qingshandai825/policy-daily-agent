package com.itheima.policydailyagent.agent.controller;

import com.itheima.policydailyagent.agent.PolicyDailyOrchestrator;
import com.itheima.policydailyagent.agent.dto.AgentRunDetails;
import com.itheima.policydailyagent.agent.dto.ReviewGateResult;
import com.itheima.policydailyagent.agent.entity.AgentRun;
import com.itheima.policydailyagent.agent.tool.AgentToolDescriptor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final PolicyDailyOrchestrator orchestrator;

    public AgentController(PolicyDailyOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @GetMapping("/tools")
    public List<AgentToolDescriptor> listTools() {
        return orchestrator.listTools();
    }

    @GetMapping("/runs/recent")
    public List<AgentRun> listRecentRuns() {
        return orchestrator.listRecentRuns();
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<?> getRun(@PathVariable Long runId) {
        try {
            AgentRunDetails details = orchestrator.getRun(runId);
            return ResponseEntity.ok(details);
        } catch (IllegalArgumentException error) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<?> getLatestRunForTask(@PathVariable Long taskId) {
        try {
            return ResponseEntity.ok(orchestrator.getLatestRunForTask(taskId));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/tasks/{taskId}/resume")
    public ResponseEntity<?> resumeAfterReview(@PathVariable Long taskId) {
        try {
            ReviewGateResult result = orchestrator.resumeAfterReview(taskId);
            if (!result.ready()) {
                return ResponseEntity.status(409).body(result);
            }
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(Map.of("message", error.getMessage()));
        } catch (RuntimeException error) {
            return ResponseEntity.internalServerError().body(Map.of("message", error.getMessage()));
        }
    }
}
