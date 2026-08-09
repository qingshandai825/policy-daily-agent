package com.itheima.policydailyagent.agent.tool.impl;

import com.itheima.policydailyagent.agent.dto.PolicyDiscoveryToolInput;
import com.itheima.policydailyagent.agent.tool.AgentTool;
import com.itheima.policydailyagent.dto.PolicyDiscoverResult;
import com.itheima.policydailyagent.service.PolicyDiscoveryService;
import org.springframework.stereotype.Component;

@Component
public class PolicyDiscoveryAgentTool implements AgentTool<PolicyDiscoveryToolInput, PolicyDiscoverResult> {

    public static final String NAME = "policy.discover";

    private final PolicyDiscoveryService policyDiscoveryService;

    public PolicyDiscoveryAgentTool(PolicyDiscoveryService policyDiscoveryService) {
        this.policyDiscoveryService = policyDiscoveryService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Discover candidate policy links and persist accepted policy evidence";
    }

    @Override
    public PolicyDiscoverResult execute(PolicyDiscoveryToolInput input) {
        return policyDiscoveryService.discoverAndSave(input.request(), input.agentRunId());
    }

    @Override
    public String summarizeInput(PolicyDiscoveryToolInput input) {
        return "source=" + input.request().listPageUrl()
                + ", dateRange=" + input.request().resolvedTargetStartDate()
                + ".." + input.request().resolvedTargetEndDate();
    }

    @Override
    public String summarizeOutput(PolicyDiscoverResult output) {
        return "taskId=" + output.taskId()
                + ", found=" + output.foundLinks()
                + ", saved=" + output.savedCount()
                + ", duplicates=" + output.duplicateCount()
                + ", filtered=" + output.filteredCount()
                + ", failed=" + output.failedCount();
    }
}
