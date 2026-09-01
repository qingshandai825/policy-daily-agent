package com.itheima.policydailyagent.dto;

public record AgentStatusView(
        boolean available,
        String provider,
        String model,
        String message
) {
}
