package com.itheima.policydailyagent.controller;

import com.itheima.policydailyagent.dto.PolicyDocumentCreateRequest;
import com.itheima.policydailyagent.entity.PolicyDocument;
import com.itheima.policydailyagent.service.PolicyDocumentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/policies")
public class PolicyDocumentController {

    private final PolicyDocumentService policyDocumentService;

    public PolicyDocumentController(PolicyDocumentService policyDocumentService) {
        this.policyDocumentService = policyDocumentService;
    }

    @PostMapping
    public ResponseEntity<?> createPolicyDocument(@Valid @RequestBody PolicyDocumentCreateRequest request) {
        try {
            PolicyDocument savedDocument = policyDocumentService.createPolicyDocument(request);
            return ResponseEntity.ok(savedDocument);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/recent")
    public ResponseEntity<List<PolicyDocument>> listRecentPolicies() {
        return ResponseEntity.ok(policyDocumentService.listRecentPolicies());
    }

    @GetMapping("/task/{taskId}")
    public ResponseEntity<List<PolicyDocument>> listPoliciesByTaskId(@PathVariable Long taskId) {
        return ResponseEntity.ok(policyDocumentService.listPoliciesByTaskId(taskId));
    }
}
