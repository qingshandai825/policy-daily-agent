package com.itheima.policydailyagent.agent.tool.impl;

import com.itheima.policydailyagent.agent.dto.DateFilterToolInput;
import com.itheima.policydailyagent.agent.tool.AgentTool;
import com.itheima.policydailyagent.dto.DateFilterResult;
import com.itheima.policydailyagent.service.PolicyDateFilterService;
import org.springframework.stereotype.Component;

@Component
public class PolicyDateFilterAgentTool implements AgentTool<DateFilterToolInput, DateFilterResult> {

    public static final String NAME = "policy.date-filter";

    private final PolicyDateFilterService policyDateFilterService;

    public PolicyDateFilterAgentTool(PolicyDateFilterService policyDateFilterService) {
        this.policyDateFilterService = policyDateFilterService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Deterministically validate the actual policy publish date against the task range";
    }

    @Override
    public DateFilterResult execute(DateFilterToolInput input) {
        return policyDateFilterService.filter(
                input.crawlResult(),
                input.targetStartDate(),
                input.targetEndDate()
        );
    }

    @Override
    public String summarizeInput(DateFilterToolInput input) {
        return "publishDate=" + input.crawlResult().publishDate()
                + ", target=" + input.targetStartDate() + ".." + input.targetEndDate();
    }
}
