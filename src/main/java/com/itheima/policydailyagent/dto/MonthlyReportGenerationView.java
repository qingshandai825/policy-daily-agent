package com.itheima.policydailyagent.dto;

import java.time.LocalDateTime;

public record MonthlyReportGenerationView(
        Long id,
        Long reportId,
        int generationNo,
        int issueNo,
        int totalIssueNo,
        String reportMonth,
        String reportTo,
        String sendTo,
        String contactInfo,
        String generatedBy,
        String fileName,
        long fileSize,
        String sha256,
        LocalDateTime createdAt
) {
}
