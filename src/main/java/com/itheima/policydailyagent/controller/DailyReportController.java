package com.itheima.policydailyagent.controller;

import com.itheima.policydailyagent.agent.PolicyDailyOrchestrator;
import com.itheima.policydailyagent.agent.exception.ReviewNotReadyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/api/daily-tasks")
public class DailyReportController {

    private final PolicyDailyOrchestrator orchestrator;

    public DailyReportController(PolicyDailyOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/{taskId}/reports")
    public ResponseEntity<?> generateDailyReport(@PathVariable Long taskId) {
        try {
            byte[] fileBytes = orchestrator.generateDailyReport(taskId);
            String fileName = "政策日报_任务" + taskId + "_"
                    + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                    + ".docx";

            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .body(fileBytes);
        } catch (ReviewNotReadyException e) {
            return ResponseEntity.status(409).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
        }
    }
}
