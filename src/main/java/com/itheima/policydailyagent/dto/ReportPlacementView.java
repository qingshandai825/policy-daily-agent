package com.itheima.policydailyagent.dto;

import com.itheima.policydailyagent.domain.report.ReportItemStatus;

public record ReportPlacementView(
        Long reportId,
        Long reportItemId,
        Long sectionId,
        ReportItemStatus status
) {
}
