package com.itheima.policydailyagent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PolicyAnalysisConfirmRequest(
        @NotNull @Min(2000) @Max(2100)
        Integer reportYear,
        @NotNull @Min(1) @Max(12)
        Integer reportMonth,
        @NotBlank(message = "月报栏目不能为空")
        String sectionCode,
        @NotBlank(message = "条目标题不能为空")
        String itemTitle,
        @NotBlank(message = "确认正文不能为空")
        String finalContent,
        @NotBlank(message = "确认人不能为空")
        String confirmedBy
) {
}
