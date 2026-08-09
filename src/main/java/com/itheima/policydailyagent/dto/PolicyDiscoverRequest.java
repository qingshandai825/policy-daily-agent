package com.itheima.policydailyagent.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.List;

public record PolicyDiscoverRequest(

        @NotBlank(message = "listPageUrl must not be blank")
        String listPageUrl,

        List<String> keywords,

        Integer maxLinks,

        Boolean autoSummarize,

        LocalDate targetStartDate,

        LocalDate targetEndDate,

        String taskName,

        Long taskId
) {
    public PolicyDiscoverRequest(
            String listPageUrl,
            List<String> keywords,
            Integer maxLinks,
            Boolean autoSummarize,
            LocalDate targetStartDate,
            LocalDate targetEndDate,
            String taskName
    ) {
        this(
                listPageUrl,
                keywords,
                maxLinks,
                autoSummarize,
                targetStartDate,
                targetEndDate,
                taskName,
                null
        );
    }

    public LocalDate resolvedTargetStartDate() {
        if (targetStartDate != null) {
            return targetStartDate;
        }
        return targetEndDate;
    }

    public LocalDate resolvedTargetEndDate() {
        if (targetEndDate != null) {
            return targetEndDate;
        }
        return targetStartDate;
    }

    public boolean hasTargetDateRange() {
        return targetStartDate != null || targetEndDate != null;
    }
}