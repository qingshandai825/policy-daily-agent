package com.itheima.policydailyagent.agent.tool;

public record AgentToolDescriptor(
        String name,
        String description,
        int maxAttempts
) {
}
