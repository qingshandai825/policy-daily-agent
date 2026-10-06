package com.itheima.policydailyagent.dto;

import com.itheima.policydailyagent.domain.analysis.AnalysisRunStatus;

import java.time.LocalDateTime;
import java.util.List;

public record PolicyAnalysisView(
        Long id,
        Long policyId,
        int versionNo,
        AnalysisRunStatus runStatus,
        String modelName,
        String promptVersion,
        PolicyBasicInfoView basicInfo,
        String coreContent,
        String relevantContent,
        String recommendedSectionCode,
        String recommendationReason,
        String generatedTitle,
        String generatedContent,
        List<String> evidence,
        String errorMessage,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        List<ReportPlacementView> placements,
        DraftQualityReport qualityReport
) {
}
