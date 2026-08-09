package com.itheima.policydailyagent.agent.dto;

import java.util.List;

public record DailyReportSynthesis(
        Long taskId,
        String overview,
        List<String> keyPoints
) {
    public DailyReportSynthesis {
        keyPoints = keyPoints == null ? List.of() : List.copyOf(keyPoints);
    }
}
