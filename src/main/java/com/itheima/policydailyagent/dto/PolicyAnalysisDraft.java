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
        List<String> evidence
) {
}
