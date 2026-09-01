package com.itheima.policydailyagent.dto;

import com.itheima.policydailyagent.domain.report.MonthlyReportStatus;
import com.itheima.policydailyagent.domain.report.ReportItemStatus;
import com.itheima.policydailyagent.domain.report.ReportSourceType;

import java.time.LocalDateTime;
import java.util.List;

public record MonthlyReportEditorView(
        Long reportId,
        int reportYear,
        int reportMonth,
        String title,
        MonthlyReportStatus status,
        String contentConfirmedBy,
        LocalDateTime contentConfirmedAt,
        LocalDateTime generatedAt,
        String outputFileName,
        String outputHash,
        String templateVersion,
        String templateHash,
        long lockVersion,
        int activeItemCount,
        boolean canConfirm,
        List<String> validationErrors,
        List<SectionView> sections,
        List<ItemView> removedItems
) {
    public record SectionView(
            Long id,
            String sectionCode,
            Long parentId,
            String sectionName,
            int sectionLevel,
            int sortOrder,
            String writingGuide,
            boolean writable,
            List<ItemView> items
    ) {
    }

    public record ItemView(
            Long id,
            Long sectionId,
            String sectionCode,
            Long policyId,
            Long analysisId,
            ReportSourceType sourceType,
            String itemTitle,
            String agentDraft,
            String finalContent,
            ReportItemStatus status,
            int sortOrder,
            String createdBy,
            String updatedBy,
            long lockVersion,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<SourceView> sources,
            List<RevisionView> revisions
    ) {
    }

    public record SourceView(
            Long id,
            ReportSourceType sourceType,
            Long policyId,
            String sourceTitle,
            String sourceUrl,
            int snapshotLength
    ) {
    }

    public record RevisionView(
            Long id,
            int revisionNo,
            String itemTitle,
            Long sectionId,
            String sectionCode,
            ReportItemStatus itemStatus,
            int sortOrder,
            String content,
            String changeType,
            String changeNote,
            String editor,
            LocalDateTime createdAt
    ) {
    }
}
