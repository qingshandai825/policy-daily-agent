package com.itheima.policydailyagent.agent.dto;

import com.itheima.policydailyagent.dto.PolicyDiscoverResult;

public record AgentCollectionOutcome(
        Long agentRunId,
        PolicyDiscoverResult result
) {
}
