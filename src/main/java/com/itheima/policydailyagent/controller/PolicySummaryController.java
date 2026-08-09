package com.itheima.policydailyagent.controller;

import com.itheima.policydailyagent.entity.PolicyDocument;
import com.itheima.policydailyagent.service.PolicySummaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai/policies")
public class PolicySummaryController {

    private final PolicySummaryService policySummaryService;

    public PolicySummaryController(PolicySummaryService policySummaryService) {
        this.policySummaryService = policySummaryService;
    }

    @PostMapping("/{id}/summary")
    public ResponseEntity<?> summarize(@PathVariable Long id) {
        try {
            PolicyDocument document = policySummaryService.summarizeById(id);
            return ResponseEntity.ok(document);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "摘要生成失败：" + e.getMessage()));
        }
    }
}