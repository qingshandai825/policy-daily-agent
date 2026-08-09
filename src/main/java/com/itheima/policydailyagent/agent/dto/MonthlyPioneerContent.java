package com.itheima.policydailyagent.agent.dto;

import java.util.List;

public record MonthlyPioneerContent(
        List<String> keyPoints,
        String overview,
        String metrics,
        String problems,
        String practices
) {
    public MonthlyPioneerContent {
        keyPoints = keyPoints == null ? List.of() : List.copyOf(keyPoints);
    }
}
