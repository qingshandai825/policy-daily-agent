package com.itheima.policydailyagent.dto;

import com.itheima.policydailyagent.domain.policy.ContentCompleteness;
import com.itheima.policydailyagent.domain.policy.PolicyAnalysisStatus;
import com.itheima.policydailyagent.domain.policy.PolicyReviewStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PolicyCandidateView(
        Long id,
        Long searchTaskId,
        String sourceId,
        String provider,
        String title,
        String sourceName,
        LocalDate publishDate,
        String sourceUrl,
        String discoveredUrl,
        String category,
        String policyType,
        String keywords,
        String shortSummary,
        String content,
        String evidenceSnippet,
        ContentCompleteness contentCompleteness,
        String contentQualityReason,
        Integer attachmentCount,
        Integer extractedAttachmentCount,
        String sourceDomain,
        String authorityLevel,
        String filterStatus,
        BigDecimal relevanceScore,
        PolicyReviewStatus reviewStatus,
        PolicyAnalysisStatus analysisStatus,
        String reviewComment,
        String reviewedBy,
        LocalDateTime reviewedAt
) {
}
