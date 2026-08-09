package com.itheima.policydailyagent.agent.dto;

import com.itheima.policydailyagent.dto.PolicyCrawlResult;

import java.time.LocalDate;

public record DateFilterToolInput(
        PolicyCrawlResult crawlResult,
        LocalDate targetStartDate,
        LocalDate targetEndDate
) {
}
