package com.itheima.policydailyagent.dto;

import com.itheima.policydailyagent.domain.policy.ContentCompleteness;
import com.itheima.policydailyagent.domain.policy.PolicyAnalysisStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AcceptedPolicyView(
        Long id,
        String title,
        String sourceName,
        LocalDate publishDate,
        String sourceUrl,
        String policyType,
        String category,
        String keywords,
        String summary,
        String content,
        ContentCompleteness contentCompleteness,
        String contentQualityReason,
        Integer attachmentCount,
        Integer extractedAttachmentCount,
        LocalDateTime contentRefreshedAt,
        List<PolicyAttachmentView> attachments,
        PolicyAnalysisStatus analysisStatus,
        String reviewedBy,
        LocalDateTime reviewedAt,
        PolicyAnalysisView latestAnalysis,
        List<PolicyAnalysisView> analyses
) {
}
