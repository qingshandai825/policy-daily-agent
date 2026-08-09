package com.itheima.policydailyagent.agent.dto;

import com.itheima.policydailyagent.dto.PolicyDiscoverRequest;

public record PolicyDiscoveryToolInput(
        Long agentRunId,
        PolicyDiscoverRequest request
) {
}
