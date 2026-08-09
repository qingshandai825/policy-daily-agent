package com.itheima.policydailyagent.agent.dto;

import com.itheima.policydailyagent.agent.entity.AgentRun;
import com.itheima.policydailyagent.agent.entity.AgentStep;

import java.util.List;

public record AgentRunDetails(
        AgentRun run,
        List<AgentStep> steps
) {
}
