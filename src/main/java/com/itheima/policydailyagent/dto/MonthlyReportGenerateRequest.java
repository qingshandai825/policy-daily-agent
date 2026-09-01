package com.itheima.policydailyagent.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MonthlyReportGenerateRequest(
        @NotNull @Min(1) Long reportId,
        @NotNull @Min(1) Integer issueNo,
        @NotNull @Min(1) Integer totalIssueNo,
        @NotBlank @Size(max = 1000) String reportTo,
        @NotBlank @Size(max = 1000) String sendTo,
        @NotBlank @Size(max = 1000) String contactInfo,
        @NotBlank @Size(max = 100) String generatedBy
) {
}
