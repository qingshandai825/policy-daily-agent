package com.itheima.policydailyagent.agent.dto;

public record DeduplicationToolInput(
        String sourceUrl,
        String contentHash
) {
}
