package com.itheima.policydailyagent.dto;

import jakarta.validation.constraints.NotBlank;

public record PolicyAnalysisRequest(
        @NotBlank(message = "操作人不能为空")
        String createdBy
) {
}
