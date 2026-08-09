package com.itheima.policydailyagent.dto;

public record MonthlyReportGenerateRequest(

        Integer reportYear,

        Integer issueNo,

        Integer totalIssueNo,

        String reportMonth,

        String statDate,

        String reportTo,

        String sendTo,

        String contactInfo,

        Long taskId
) {

    public MonthlyReportGenerateRequest(
            Integer reportYear,
            Integer issueNo,
            Integer totalIssueNo,
            String reportMonth,
            String statDate,
            String reportTo,
            String sendTo,
            String contactInfo
    ) {
        this(reportYear, issueNo, totalIssueNo, reportMonth, statDate, reportTo, sendTo, contactInfo, null);
    }

    public MonthlyReportGenerateRequest withTaskId(Long value) {
        return new MonthlyReportGenerateRequest(
                reportYear,
                issueNo,
                totalIssueNo,
                reportMonth,
                statDate,
                reportTo,
                sendTo,
                contactInfo,
                value
        );
    }
}
