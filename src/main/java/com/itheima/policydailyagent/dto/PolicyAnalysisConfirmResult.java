package com.itheima.policydailyagent.dto;

import com.itheima.policydailyagent.domain.report.ReportItemStatus;

public record PolicyAnalysisConfirmResult(
        Long reportId,
        Long reportItemId,
        String reportTitle,
        String sectionCode,
        ReportItemStatus status
) {
}
