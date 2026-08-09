package com.itheima.policydailyagent.controller;

import com.itheima.policydailyagent.dto.MonthlyReportGenerateRequest;
import com.itheima.policydailyagent.service.MonthlyReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/reports")
public class MonthlyReportController {

    private final MonthlyReportService monthlyReportService;

    public MonthlyReportController(MonthlyReportService monthlyReportService) {
        this.monthlyReportService = monthlyReportService;
    }

    @GetMapping("/monthly")
    public ResponseEntity<byte[]> downloadMonthlyReportWithDefaultParams() {
        return buildWordResponse(
                monthlyReportService.generateMonthlyReport(null)
        );
    }

    @PostMapping("/monthly")
    public ResponseEntity<byte[]> downloadMonthlyReport(
            @RequestBody(required = false) MonthlyReportGenerateRequest request
    ) {
        return buildWordResponse(
                monthlyReportService.generateMonthlyReport(request)
        );
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