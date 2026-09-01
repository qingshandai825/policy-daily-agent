package com.itheima.policydailyagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReorderReportSectionRequest(
        @NotEmpty List<Long> orderedItemIds,
        @NotBlank @Size(max = 100) String editor
) {
}
