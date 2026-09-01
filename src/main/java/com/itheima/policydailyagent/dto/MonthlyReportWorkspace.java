package com.itheima.policydailyagent.dto;

import com.itheima.policydailyagent.domain.report.MonthlyReport;
import com.itheima.policydailyagent.domain.report.MonthlyReportItem;
import com.itheima.policydailyagent.domain.report.ReportSection;

import java.util.List;

public record MonthlyReportWorkspace(
        MonthlyReport report,
        List<ReportSection> sections,
        List<MonthlyReportItem> items
) {
}
