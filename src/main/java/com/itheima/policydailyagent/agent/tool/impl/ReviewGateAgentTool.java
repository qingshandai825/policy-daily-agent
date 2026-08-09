package com.itheima.policydailyagent.agent.tool.impl;

import com.itheima.policydailyagent.agent.dto.ReviewGateResult;
import com.itheima.policydailyagent.agent.tool.AgentTool;
import com.itheima.policydailyagent.dto.ReviewTaskSummary;
import com.itheima.policydailyagent.service.PolicyReviewService;
import org.springframework.stereotype.Component;

@Component
public class ReviewGateAgentTool implements AgentTool<Long, ReviewGateResult> {

    public static final String NAME = "policy.review-gate";

    private final PolicyReviewService policyReviewService;

    public ReviewGateAgentTool(PolicyReviewService policyReviewService) {
        this.policyReviewService = policyReviewService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Pause or resume the agent according to persisted human review decisions";
    }

    @Override
    public ReviewGateResult execute(Long taskId) {
        ReviewTaskSummary summary = policyReviewService.summarizeTask(taskId);
        if (summary.totalCount() == 0) {
            return new ReviewGateResult(false, "任务中没有可选择的政策素材", summary);
        }
        if (summary.approvedCount() == 0) {
            return new ReviewGateResult(false, "请至少勾选一条政策素材", summary);
        }
        return new ReviewGateResult(true, "已选择政策素材，可以生成日报", summary);
    }

    @Override
    public String summarizeInput(Long input) {
        return "taskId=" + input;
    }

    @Override
    public String summarizeOutput(ReviewGateResult output) {
        return "ready=" + output.ready()
                + ", approved=" + output.summary().approvedCount()
                + ", pending=" + output.summary().pendingCount()
                + ", needsEdit=" + output.summary().needsEditCount();
    }
}
