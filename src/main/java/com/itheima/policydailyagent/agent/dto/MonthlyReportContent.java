package com.itheima.policydailyagent.agent.dto;

public record MonthlyReportContent(
        int reportYear,
        int issueNo,
        int totalIssueNo,
        String reportMonth,
        String statDate,
        String reportTo,
        String sendTo,
        String contactInfo,
        MonthlyReportSection national,
        MonthlyReportSection provincial,
        MonthlyPioneerContent pioneer,
        MonthlyReportSection cases,
        MonthlyReportSection trends
) {
}
