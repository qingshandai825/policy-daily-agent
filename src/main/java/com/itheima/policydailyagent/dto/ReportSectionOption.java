package com.itheima.policydailyagent.dto;

public record ReportSectionOption(
        Long id,
        String sectionCode,
        String sectionName,
        int sectionLevel,
        int sortOrder,
        String writingGuide
) {
}
