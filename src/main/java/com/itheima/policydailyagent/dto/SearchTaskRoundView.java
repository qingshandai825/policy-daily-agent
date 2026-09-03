package com.itheima.policydailyagent.dto;

import com.itheima.policydailyagent.domain.search.SearchTaskRoundStatus;

import java.time.LocalDateTime;

/**
 * 搜索任务轮次的只读视图，供轮次追溯 API 返回，不含任何可写字段。
 */
public record SearchTaskRoundView(
        Long id,
        Long searchTaskId,
        int roundNo,
        SearchTaskRoundStatus status,
        String planJson,
        String keywordsJson,
        String targetSourcesJson,
        String coverageBeforeJson,
        String coverageAfterJson,
        int foundCount,
        int savedCount,
        int duplicateCount,
        int filteredCount,
        int failedCount,
        int newAssociationCount,
        String stopReason,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt
) {
}
