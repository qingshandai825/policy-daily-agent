package com.itheima.policydailyagent.dto;

import java.util.List;

public record PolicyAnalysisDraft(
        PolicyBasicInfoView basicInfo,
        String coreContent,
        String relevantContent,
        String recommendedSectionCode,
        String recommendationReason,
        String generatedTitle,
        String generatedContent,
        List<String> evidence,
        DraftQualityReport qualityReport
) {
    public PolicyAnalysisDraft(PolicyBasicInfoView basicInfo, String coreContent, String relevantContent,
            String recommendedSectionCode, String recommendationReason, String generatedTitle,
            String generatedContent, List<String> evidence) {
        this(basicInfo, coreContent, relevantContent, recommendedSectionCode, recommendationReason,
                generatedTitle, generatedContent, evidence, null);
    }

    public PolicyAnalysisDraft withQualityReport(DraftQualityReport report) {
        return new PolicyAnalysisDraft(basicInfo, coreContent, relevantContent, recommendedSectionCode,
                recommendationReason, generatedTitle, generatedContent, evidence, report);
    }
}
