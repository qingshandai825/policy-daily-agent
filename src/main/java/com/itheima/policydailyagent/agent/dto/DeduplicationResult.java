package com.itheima.policydailyagent.agent.dto;

public record DeduplicationResult(
        boolean duplicate,
        String reason
) {
}
