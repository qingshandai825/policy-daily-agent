package com.itheima.policydailyagent.dto;

import com.itheima.policydailyagent.domain.policy.AttachmentExtractionStatus;

import java.time.LocalDateTime;

public record PolicyAttachmentView(
        Long id,
        String fileName,
        String sourceUrl,
        String fileType,
        String contentType,
        AttachmentExtractionStatus extractionStatus,
        Integer contentLength,
        String contentHash,
        String errorMessage,
        LocalDateTime extractedAt
) {
}
