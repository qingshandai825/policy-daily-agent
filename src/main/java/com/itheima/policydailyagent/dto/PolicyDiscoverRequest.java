package com.itheima.policydailyagent.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.List;

public record PolicyDiscoverRequest(

        @NotBlank(message = "listPageUrl must not be blank")
        String listPageUrl,

        List<String> keywords,

        Integer maxLinks,

        LocalDate targetStartDate,

        LocalDate targetEndDate,

        String taskName,

        String reportMonth
) {
    public LocalDate resolvedTargetStartDate() {
        if (targetStartDate != null) {
            return targetStartDate;
        }
        return targetEndDate != null ? targetEndDate : LocalDate.now();
    }

    public LocalDate resolvedTargetEndDate() {
        if (targetEndDate != null) {
            return targetEndDate;
        }
        return targetStartDate != null ? targetStartDate : LocalDate.now();
    }
}
