package com.itheima.policydailyagent.dto;

public record PolicyReviewRequest(
        String reviewedBy,
        String reviewComment
) {
}
