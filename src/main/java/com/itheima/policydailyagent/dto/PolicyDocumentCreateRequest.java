package com.itheima.policydailyagent.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PolicyDocumentCreateRequest(

        @NotBlank(message = "title must not be blank")
        String title,

        String sourceName,

        LocalDate publishDate,

        @NotBlank(message = "sourceUrl must not be blank")
        String sourceUrl,

        String content,

        String category,

        Long dailyTaskId,

        LocalDateTime retrievedAt,

        String sourceDomain,

        String sourceType,

        String authorityLevel,

        String dateSource,

        String dateText,

        String dateConfidence,

        String contentHash,

        String evidenceSnippet,

        String filterStatus,

        String filterReason
) {
}
