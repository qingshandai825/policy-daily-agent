package com.itheima.policydailyagent.agent.tool.impl;

import com.itheima.policydailyagent.agent.dto.DailyReportSynthesis;
import com.itheima.policydailyagent.agent.tool.AgentTool;
import com.itheima.policydailyagent.agent.tool.ToolRetryPolicy;
import com.itheima.policydailyagent.service.DailyReportService;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class DailyReportAgentTool implements AgentTool<DailyReportSynthesis, byte[]> {

    public static final String NAME = "report.generate-daily";

    private final DailyReportService dailyReportService;

    public DailyReportAgentTool(DailyReportService dailyReportService) {
        this.dailyReportService = dailyReportService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Generate a deterministic Word report from Agent synthesis and selected policy evidence";
    }

    @Override
    public byte[] execute(DailyReportSynthesis synthesis) {
        return dailyReportService.generateDailyReport(synthesis);
    }

    @Override
    public ToolRetryPolicy retryPolicy() {
        return new ToolRetryPolicy(2, Duration.ofMillis(300), 2.0, Duration.ofSeconds(1));
    }

    @Override
    public String summarizeInput(DailyReportSynthesis input) {
        return "taskId=" + input.taskId() + ", keyPoints=" + input.keyPoints().size();
    }

    @Override
    public String summarizeOutput(byte[] output) {
        return "wordBytes=" + output.length;
    }
}