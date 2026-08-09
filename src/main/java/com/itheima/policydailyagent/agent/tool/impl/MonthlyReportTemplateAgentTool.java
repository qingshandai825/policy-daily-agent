package com.itheima.policydailyagent.agent.tool.impl;

import com.itheima.policydailyagent.agent.dto.MonthlyReportContent;
import com.itheima.policydailyagent.agent.tool.AgentTool;
import com.itheima.policydailyagent.service.MonthlyReportTemplateService;
import org.springframework.stereotype.Component;

@Component
public class MonthlyReportTemplateAgentTool implements AgentTool<MonthlyReportContent, byte[]> {

    public static final String NAME = "report.fill-monthly-template";

    private final MonthlyReportTemplateService monthlyReportTemplateService;

    public MonthlyReportTemplateAgentTool(MonthlyReportTemplateService monthlyReportTemplateService) {
        this.monthlyReportTemplateService = monthlyReportTemplateService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Fill the original Word template deterministically with structured monthly content";
    }

    @Override
    public byte[] execute(MonthlyReportContent input) {
        return monthlyReportTemplateService.fill(input);
    }

    @Override
    public String summarizeInput(MonthlyReportContent input) {
        return "month=" + input.reportMonth() + ", issue=" + input.issueNo();
    }

    @Override
    public String summarizeOutput(byte[] output) {
        return "wordBytes=" + output.length;
    }
}
