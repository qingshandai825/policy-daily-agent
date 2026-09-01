package com.itheima.policydailyagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MonthlyReportActionRequest(
        @NotBlank @Size(max = 100) String operator,
        @Size(max = 1000) String reason
) {
}
