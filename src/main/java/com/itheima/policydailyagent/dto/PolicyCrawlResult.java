package com.itheima.policydailyagent.dto;

import com.itheima.policydailyagent.domain.policy.ContentCompleteness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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

        String evidenceSnippet,

        String cleanedContent,

        ContentCompleteness contentCompleteness,

        String contentQualityReason,

        List<PolicyAttachmentCrawlResult> attachments
) {

    public PolicyCrawlResult(
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
        this(
                title, sourceName, publishDate, sourceUrl, content, retrievedAt,
                sourceDomain, sourceType, authorityLevel, dateSource, dateText,
                dateConfidence, contentHash, evidenceSnippet, content,
                ContentCompleteness.UNKNOWN, "历史抓取结果未执行附件完整性检查", List.of()
        );
    }
}
