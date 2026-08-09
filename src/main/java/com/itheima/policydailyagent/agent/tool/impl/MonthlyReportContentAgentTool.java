package com.itheima.policydailyagent.agent.tool.impl;

import com.itheima.policydailyagent.agent.dto.MonthlyReportContent;
import com.itheima.policydailyagent.agent.tool.AgentTool;
import com.itheima.policydailyagent.agent.tool.ToolRetryPolicy;
import com.itheima.policydailyagent.dto.MonthlyReportGenerateRequest;
import com.itheima.policydailyagent.service.MonthlyReportContentService;
import org.springframework.stereotype.Component;

@Component
public class MonthlyReportContentAgentTool
        implements AgentTool<MonthlyReportGenerateRequest, MonthlyReportContent> {

    public static final String NAME = "report.generate-monthly-content";

    private final MonthlyReportContentService monthlyReportContentService;

    public MonthlyReportContentAgentTool(MonthlyReportContentService monthlyReportContentService) {
        this.monthlyReportContentService = monthlyReportContentService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Generate structured monthly report content from human-approved policy evidence";
    }

    @Override
    public MonthlyReportContent execute(MonthlyReportGenerateRequest input) {
        return monthlyReportContentService.generate(input);
    }

    @Override
    public ToolRetryPolicy retryPolicy() {
        return ToolRetryPolicy.externalCall();
    }

    @Override
    public String summarizeOutput(MonthlyReportContent output) {
        return "month=" + output.reportMonth()
                + ", national=" + output.national().items().size()
                + ", provincial=" + output.provincial().items().size()
                + ", cases=" + output.cases().items().size()
                + ", trends=" + output.trends().items().size();
    }
}
