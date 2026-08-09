package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.dto.DateFilterResult;
import com.itheima.policydailyagent.dto.PolicyCrawlResult;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class PolicyDateFilterService {

    public DateFilterResult filter(
            PolicyCrawlResult crawlResult,
            LocalDate targetStartDate,
            LocalDate targetEndDate
    ) {
        if (targetStartDate == null && targetEndDate == null) {
            return new DateFilterResult(true, "ACCEPTED", "No target date range configured.");
        }

        if (crawlResult == null || crawlResult.publishDate() == null) {
            return new DateFilterResult(false, "FILTERED_DATE_UNKNOWN", "Publish date was not extracted.");
        }

        LocalDate publishDate = crawlResult.publishDate();

        if (targetStartDate != null && publishDate.isBefore(targetStartDate)) {
            return new DateFilterResult(false, "FILTERED_DATE_BEFORE_RANGE", "Publish date is before target start date.");
        }

        if (targetEndDate != null && publishDate.isAfter(targetEndDate)) {
            return new DateFilterResult(false, "FILTERED_DATE_AFTER_RANGE", "Publish date is after target end date.");
        }

        return new DateFilterResult(true, "ACCEPTED", "Publish date is within target date range.");
    }
}
