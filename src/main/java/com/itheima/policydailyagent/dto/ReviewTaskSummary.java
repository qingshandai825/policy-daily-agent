package com.itheima.policydailyagent.dto;

public record ReviewTaskSummary(
        Long searchTaskId,
        long totalCount,
        long pendingCount,
        long acceptedCount,
        long rejectedCount,
        long deferredCount
) {
}
