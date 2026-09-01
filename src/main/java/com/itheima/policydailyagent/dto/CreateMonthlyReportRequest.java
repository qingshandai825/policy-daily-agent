package com.itheima.policydailyagent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateMonthlyReportRequest(
        @NotNull @Min(2000) @Max(2100) Integer reportYear,
        @NotNull @Min(1) @Max(12) Integer reportMonth
) {
}
