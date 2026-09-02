package com.itheima.policydailyagent.dto;

import java.time.LocalDate;
import java.util.List;

public record SearchTaskRunRequest(
        String taskName,
        String reportMonth,
        LocalDate targetStartDate,
        LocalDate targetEndDate,
        List<String> keywords,
        List<String> sourceIds,
        Integer maxLinksPerSource,
        Boolean filterByKeyword
) {
}
