package com.itheima.policydailyagent.dto;

import com.itheima.policydailyagent.domain.policy.AttachmentExtractionStatus;

public record PolicyAttachmentCrawlResult(
        String fileName,
        String sourceUrl,
        String fileType,
        String contentType,
        AttachmentExtractionStatus extractionStatus,
        String extractedContent,
        String contentHash,
        Integer contentLength,
        String errorMessage
) {
}
