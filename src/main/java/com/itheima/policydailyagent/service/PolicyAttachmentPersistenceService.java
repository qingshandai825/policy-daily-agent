package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.domain.policy.AttachmentExtractionStatus;
import com.itheima.policydailyagent.dto.PolicyAttachmentCrawlResult;
import com.itheima.policydailyagent.dto.PolicyAttachmentView;
import com.itheima.policydailyagent.entity.PolicyAttachment;
import com.itheima.policydailyagent.repository.PolicyAttachmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PolicyAttachmentPersistenceService {

    private final PolicyAttachmentRepository attachmentRepository;

    public PolicyAttachmentPersistenceService(PolicyAttachmentRepository attachmentRepository) {
        this.attachmentRepository = attachmentRepository;
    }

    @Transactional
    public void upsert(Long policyId, List<PolicyAttachmentCrawlResult> results) {
        if (policyId == null || results == null) {
            return;
        }
        for (PolicyAttachmentCrawlResult result : results) {
            PolicyAttachment attachment = attachmentRepository
                    .findByPolicyIdAndSourceUrl(policyId, result.sourceUrl())
                    .orElseGet(PolicyAttachment::new);
            attachment.setPolicyId(policyId);
            attachment.setFileName(result.fileName());
            attachment.setSourceUrl(result.sourceUrl());
            attachment.setFileType(result.fileType());
            attachment.setContentType(result.contentType());
            attachment.setExtractionStatus(result.extractionStatus());
            attachment.setExtractedContent(result.extractedContent());
            attachment.setContentHash(result.contentHash());
            attachment.setContentLength(result.contentLength());
            attachment.setErrorMessage(result.errorMessage());
            attachment.setDetectedAt(LocalDateTime.now());
            attachment.setExtractedAt(
                    result.extractionStatus() == AttachmentExtractionStatus.SUCCEEDED
                            ? LocalDateTime.now()
                            : null
            );
            attachmentRepository.save(attachment);
        }
    }

    @Transactional(readOnly = true)
    public List<PolicyAttachmentView> listViews(Long policyId) {
        return attachmentRepository.findByPolicyIdOrderByIdAsc(policyId)
                .stream()
                .map(this::toView)
                .toList();
    }

    private PolicyAttachmentView toView(PolicyAttachment attachment) {
        return new PolicyAttachmentView(
                attachment.getId(),
                attachment.getFileName(),
                attachment.getSourceUrl(),
                attachment.getFileType(),
                attachment.getContentType(),
                attachment.getExtractionStatus(),
                attachment.getContentLength(),
                attachment.getContentHash(),
                attachment.getErrorMessage(),
                attachment.getExtractedAt()
        );
    }
}
