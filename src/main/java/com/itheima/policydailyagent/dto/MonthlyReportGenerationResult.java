package com.itheima.policydailyagent.dto;

public record MonthlyReportGenerationResult(
        Long generationId,
        Long reportId,
        int generationNo,
        String fileName,
        String contentType,
        long fileSize,
        String sha256,
        byte[] content
) {
}
