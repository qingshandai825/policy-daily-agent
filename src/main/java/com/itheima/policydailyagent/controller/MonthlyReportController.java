package com.itheima.policydailyagent.controller;

import com.itheima.policydailyagent.dto.MonthlyReportGenerateRequest;
import com.itheima.policydailyagent.dto.MonthlyReportGenerationResult;
import com.itheima.policydailyagent.service.MonthlyReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class MonthlyReportController {

    private final MonthlyReportService monthlyReportService;

    public MonthlyReportController(MonthlyReportService monthlyReportService) {
        this.monthlyReportService = monthlyReportService;
    }

    @PostMapping("/monthly")
    public ResponseEntity<?> downloadMonthlyReport(
            @Valid @RequestBody MonthlyReportGenerateRequest request
    ) {
        try {
            return buildWordResponse(monthlyReportService.generateMonthlyReport(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/monthly/{reportId}/generations")
    public ResponseEntity<?> listGenerations(@PathVariable Long reportId) {
        try {
            return ResponseEntity.ok(monthlyReportService.listGenerations(reportId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/monthly/{reportId}/generations/{generationId}/download")
    public ResponseEntity<?> downloadGeneration(
            @PathVariable Long reportId,
            @PathVariable Long generationId
    ) {
        try {
            return buildWordResponse(monthlyReportService.downloadGeneration(reportId, generationId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private ResponseEntity<byte[]> buildWordResponse(MonthlyReportGenerationResult result) {
        String encodedFileName = URLEncoder.encode(result.fileName(), StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .header("X-Report-Generation-Id", String.valueOf(result.generationId()))
                .header("X-Report-SHA256", result.sha256())
                .contentLength(result.fileSize())
                .contentType(MediaType.parseMediaType(result.contentType()))
                .body(result.content());
    }
}
