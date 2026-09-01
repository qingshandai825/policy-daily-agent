package com.itheima.policydailyagent.dto;

import com.itheima.policydailyagent.domain.policy.ContentCompleteness;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PolicyDocumentCreateRequest(

        @NotBlank(message = "title must not be blank")
        String title,

        String sourceName,

        LocalDate publishDate,

        @NotBlank(message = "sourceUrl must not be blank")
        String sourceUrl,

        String content,

        String category,

        Long searchTaskId,

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

        String filterReason,

        String cleanedContent,

        ContentCompleteness contentCompleteness,

        String contentQualityReason,

        List<PolicyAttachmentCrawlResult> attachments
) {

    public PolicyDocumentCreateRequest(
            String title,
            String sourceName,
            LocalDate publishDate,
            String sourceUrl,
            String content,
            String category,
            Long searchTaskId,
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
        this(
                title, sourceName, publishDate, sourceUrl, content, category,
                searchTaskId, retrievedAt, sourceDomain, sourceType, authorityLevel,
                dateSource, dateText, dateConfidence, contentHash, evidenceSnippet,
                filterStatus, filterReason, content, ContentCompleteness.UNKNOWN,
                "创建请求未提供附件完整性检查结果", List.of()
        );
    }
}
