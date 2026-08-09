package com.itheima.policydailyagent.agent.tool;

import com.itheima.policydailyagent.agent.model.AgentStage;

public record AgentToolCall(
        Long agentRunId,
        Long dailyTaskId,
        AgentStage stage
) {
}
