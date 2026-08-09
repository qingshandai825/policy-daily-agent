package com.itheima.policydailyagent.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PolicyCrawlResult(

        String title,

        String sourceName,

        LocalDate publishDate,

        String sourceUrl,

        String content,

        LocalDateTime retrievedAt,

        String sourceDomain,

        String sourceType,

        String authorityLevel,

        String dateSource,

        String dateText,

        String dateConfidence,

        String contentHash,

        String evidenceSnippet
) {
}
