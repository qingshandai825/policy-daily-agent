package com.itheima.policydailyagent.controller;

import com.itheima.policydailyagent.agent.PolicyDailyOrchestrator;
import com.itheima.policydailyagent.agent.exception.ReviewNotReadyException;
import com.itheima.policydailyagent.dto.MonthlyReportGenerateRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class MonthlyReportController {

    private final PolicyDailyOrchestrator policyDailyOrchestrator;

    public MonthlyReportController(PolicyDailyOrchestrator policyDailyOrchestrator) {
        this.policyDailyOrchestrator = policyDailyOrchestrator;
    }

    @GetMapping("/monthly")
    public ResponseEntity<byte[]> downloadMonthlyReportWithDefaultParams() {
        return buildWordResponse(
                policyDailyOrchestrator.generateMonthlyReport(null)
        );
    }

    @PostMapping("/monthly")
    public ResponseEntity<byte[]> downloadMonthlyReport(
            @RequestBody(required = false) MonthlyReportGenerateRequest request
    ) {
        return buildWordResponse(
                policyDailyOrchestrator.generateMonthlyReport(request)
        );
    }

    @PostMapping("/monthly/tasks/{taskId}")
    public ResponseEntity<?> downloadMonthlyReportForTask(
            @PathVariable Long taskId,
            @RequestBody(required = false) MonthlyReportGenerateRequest request
    ) {
        MonthlyReportGenerateRequest scopedRequest = request == null
                ? new MonthlyReportGenerateRequest(null, null, null, null, null, null, null, null, taskId)
                : request.withTaskId(taskId);
        try {
            return buildWordResponse(policyDailyOrchestrator.generateMonthlyReport(scopedRequest));
        } catch (ReviewNotReadyException error) {
            return ResponseEntity.status(409).body(Map.of("message", error.getMessage()));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(Map.of("message", error.getMessage()));
        } catch (RuntimeException error) {
            return ResponseEntity.internalServerError().body(Map.of("message", error.getMessage()));
        }
    }

    private ResponseEntity<byte[]> buildWordResponse(byte[] fileBytes) {
        String fileName = "山东省人工智能赋能制造业工作月报_"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + ".docx";

        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(fileBytes);
    }
}