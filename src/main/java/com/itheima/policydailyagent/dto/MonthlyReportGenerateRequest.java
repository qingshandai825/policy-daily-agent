package com.itheima.policydailyagent.dto;

public record MonthlyReportGenerateRequest(

        Integer reportYear,

        Integer issueNo,

        Integer totalIssueNo,

        String reportMonth,

        String statDate,

        String reportTo,

        String sendTo,

        String contactInfo
) {
}
