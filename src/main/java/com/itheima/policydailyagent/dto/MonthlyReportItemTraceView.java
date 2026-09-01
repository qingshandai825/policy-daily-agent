package com.itheima.policydailyagent.dto;

import com.itheima.policydailyagent.domain.report.ReportSourceType;

import java.time.LocalDateTime;
import java.util.List;

public record MonthlyReportItemTraceView(
        Long reportId,
        Long reportItemId,
        Long policyId,
        Long analysisId,
        String agentDraft,
        List<SourceTraceView> sources,
        List<MonthlyReportEditorView.RevisionView> revisions
) {
    public record SourceTraceView(
            Long id,
            ReportSourceType sourceType,
            Long policyId,
            String sourceTitle,
            String sourceUrl,
            String sourceContentSnapshot,
            LocalDateTime createdAt
    ) {
    }
}
