package com.itheima.policydailyagent.dto;

import java.time.LocalDate;

public record PolicyReviewUpdateRequest(
        String title,
        String sourceName,
        LocalDate publishDate,
        String category,
        String summary,
        String reviewComment,
        String reviewedBy
) {
}
