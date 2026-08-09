package com.itheima.policydailyagent.agent.tool.impl;

import com.itheima.policydailyagent.agent.dto.DailyReportSynthesis;
import com.itheima.policydailyagent.agent.tool.AgentTool;
import com.itheima.policydailyagent.agent.tool.ToolRetryPolicy;
import com.itheima.policydailyagent.service.DailyReportSynthesisService;
import org.springframework.stereotype.Component;

@Component
public class DailyReportSynthesisAgentTool implements AgentTool<Long, DailyReportSynthesis> {

    public static final String NAME = "report.synthesize-selected";

    private final DailyReportSynthesisService dailyReportSynthesisService;

    public DailyReportSynthesisAgentTool(DailyReportSynthesisService dailyReportSynthesisService) {
        this.dailyReportSynthesisService = dailyReportSynthesisService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Synthesize all human-selected policy evidence into grounded report content";
    }

    @Override
    public DailyReportSynthesis execute(Long taskId) {
        return dailyReportSynthesisService.synthesize(taskId);
    }

    @Override
    public ToolRetryPolicy retryPolicy() {
        return ToolRetryPolicy.externalCall();
    }

    @Override
    public String summarizeInput(Long taskId) {
        return "taskId=" + taskId;
    }

    @Override
    public String summarizeOutput(DailyReportSynthesis output) {
        return "taskId=" + output.taskId() + ", keyPoints=" + output.keyPoints().size();
    }
}
