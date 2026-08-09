package com.itheima.policydailyagent.dto;

public record ReviewTaskSummary(
        Long taskId,
        long totalCount,
        long pendingCount,
        long approvedCount,
        long rejectedCount,
        long needsEditCount
) {
}
