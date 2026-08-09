package com.itheima.policydailyagent.agent.tool.impl;

import com.itheima.policydailyagent.agent.tool.AgentTool;
import com.itheima.policydailyagent.agent.tool.ToolRetryPolicy;
import com.itheima.policydailyagent.entity.PolicyDocument;
import com.itheima.policydailyagent.service.PolicySummaryService;
import org.springframework.stereotype.Component;

@Component
public class PolicySummaryAgentTool implements AgentTool<Long, PolicyDocument> {

    public static final String NAME = "policy.summarize";

    private final PolicySummaryService policySummaryService;

    public PolicySummaryAgentTool(PolicySummaryService policySummaryService) {
        this.policySummaryService = policySummaryService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Generate a source-grounded policy summary with the configured language model";
    }

    @Override
    public PolicyDocument execute(Long policyDocumentId) {
        return policySummaryService.summarizeById(policyDocumentId);
    }

    @Override
    public ToolRetryPolicy retryPolicy() {
        return ToolRetryPolicy.externalCall();
    }

    @Override
    public String summarizeInput(Long input) {
        return "policyDocumentId=" + input;
    }

    @Override
    public String summarizeOutput(PolicyDocument output) {
        return "policyDocumentId=" + output.getId() + ", status=" + output.getStatus();
    }
}
