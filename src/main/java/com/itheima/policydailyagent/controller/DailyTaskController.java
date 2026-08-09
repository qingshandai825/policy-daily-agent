package com.itheima.policydailyagent.controller;

import com.itheima.policydailyagent.entity.DailyTask;
import com.itheima.policydailyagent.service.DailyTaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class DailyTaskController {

    private final DailyTaskService dailyTaskService;

    public DailyTaskController(DailyTaskService dailyTaskService) {
        this.dailyTaskService = dailyTaskService;
    }

    @GetMapping("/recent")
    public ResponseEntity<List<DailyTask>> listRecentTasks() {
        return ResponseEntity.ok(dailyTaskService.listRecentTasks());
    }
}
