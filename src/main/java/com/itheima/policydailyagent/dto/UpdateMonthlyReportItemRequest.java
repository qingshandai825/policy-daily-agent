package com.itheima.policydailyagent.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateMonthlyReportItemRequest(
        @NotBlank @Size(max = 100) String sectionCode,
        @NotBlank @Size(max = 500) String itemTitle,
        @NotBlank String finalContent,
        @NotBlank @Size(max = 100) String editor,
        @NotNull @Min(0) Long lockVersion
) {
}
