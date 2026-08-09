package com.itheima.policydailyagent.agent.dto;

import com.itheima.policydailyagent.dto.ReviewTaskSummary;

public record ReviewGateResult(
        boolean ready,
        String reason,
        ReviewTaskSummary summary
) {
}
