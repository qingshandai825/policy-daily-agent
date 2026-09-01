package com.itheima.policydailyagent.dto;

import com.itheima.policydailyagent.domain.search.SearchTaskStatus;

import java.util.List;

public record SearchTaskRunResult(
        Long taskId,
        SearchTaskStatus status,
        int foundCount,
        int savedCount,
        int duplicateCount,
        int filteredCount,
        int failedCount,
        int associatedCount,
        List<String> messages
) {
}
