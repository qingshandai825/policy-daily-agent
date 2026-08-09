package com.itheima.policydailyagent.agent.dto;

import java.util.List;

public record MonthlyReportSection(
        List<String> keyPoints,
        List<MonthlyReportItem> items
) {
    public MonthlyReportSection {
        keyPoints = keyPoints == null ? List.of() : List.copyOf(keyPoints);
        items = items == null ? List.of() : List.copyOf(items);
    }
}
