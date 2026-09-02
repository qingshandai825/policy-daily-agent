package com.itheima.policydailyagent.controller;

import com.itheima.policydailyagent.domain.search.SearchTask;
import com.itheima.policydailyagent.dto.SearchTaskRunRequest;
import com.itheima.policydailyagent.dto.SearchTaskRunResult;
import com.itheima.policydailyagent.service.SearchTaskService;
import com.itheima.policydailyagent.service.search.PolicySearchOrchestrator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/search-tasks")
public class SearchTaskController {

    private final SearchTaskService searchTaskService;
    private final PolicySearchOrchestrator searchOrchestrator;

    public SearchTaskController(
            SearchTaskService searchTaskService,
            PolicySearchOrchestrator searchOrchestrator
    ) {
        this.searchTaskService = searchTaskService;
        this.searchOrchestrator = searchOrchestrator;
    }

    @PostMapping("/run")
    public ResponseEntity<?> run(@RequestBody SearchTaskRunRequest request) {
        try {
            SearchTaskRunResult result = searchOrchestrator.run(request);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<?> getTask(@PathVariable Long taskId) {
        try {
            return ResponseEntity.ok(searchTaskService.getTask(taskId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/recent")
    public ResponseEntity<List<SearchTask>> listRecentTasks() {
        return ResponseEntity.ok(searchTaskService.listRecentTasks());
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<?> deleteTask(@PathVariable Long taskId) {
        try {
            searchTaskService.deleteTask(taskId);
            return ResponseEntity.ok(Map.of("message", "搜索任务已删除"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
