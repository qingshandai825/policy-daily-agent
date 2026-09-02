package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.domain.policy.AttachmentExtractionStatus;
import com.itheima.policydailyagent.domain.policy.ContentCompleteness;
import com.itheima.policydailyagent.dto.PolicyAttachmentCrawlResult;
import com.itheima.policydailyagent.dto.PolicyDocumentCreateRequest;
import com.itheima.policydailyagent.entity.PolicyDocument;
import com.itheima.policydailyagent.repository.PolicyDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PolicyDocumentService {

    private final PolicyDocumentRepository policyDocumentRepository;
    private final PolicyAttachmentPersistenceService attachmentPersistenceService;

    public PolicyDocumentService(
            PolicyDocumentRepository policyDocumentRepository,
            PolicyAttachmentPersistenceService attachmentPersistenceService
    ) {
        this.policyDocumentRepository = policyDocumentRepository;
        this.attachmentPersistenceService = attachmentPersistenceService;
    }

    @Transactional
    public PolicyDocument createPolicyDocument(PolicyDocumentCreateRequest request) {
        if (policyDocumentRepository.existsBySourceUrl(request.sourceUrl())) {
            throw new IllegalArgumentException("该政策链接已存在，不能重复保存");
        }

        List<PolicyAttachmentCrawlResult> attachments = request.attachments() == null
                ? List.of()
                : request.attachments();

        PolicyDocument document = new PolicyDocument();
        document.setTitle(request.title());
        document.setSourceName(request.sourceName());
        document.setPublishDate(request.publishDate());
        document.setSourceUrl(request.sourceUrl());
        document.setStatus("RAW");
        document.setContent(request.content());
        document.setCleanedContent(hasText(request.cleanedContent())
                ? request.cleanedContent()
                : request.content());
        document.setCategory(request.category());
        document.setSearchTaskId(request.searchTaskId());
        document.setRetrievedAt(request.retrievedAt());
        document.setSourceDomain(request.sourceDomain());
        document.setSourceType(request.sourceType());
        document.setAuthorityLevel(request.authorityLevel());
        document.setDateSource(request.dateSource());
        document.setDateText(request.dateText());
        document.setDateConfidence(request.dateConfidence());
        document.setContentHash(request.contentHash());
        document.setEvidenceSnippet(request.evidenceSnippet());
        document.setFilterStatus(request.filterStatus());
        document.setFilterReason(request.filterReason());
        document.setContentCompleteness(request.contentCompleteness() == null
                ? ContentCompleteness.UNKNOWN
                : request.contentCompleteness());
        document.setContentQualityReason(request.contentQualityReason());
        document.setAttachmentCount(attachments.size());
        document.setExtractedAttachmentCount((int) attachments.stream()
                .filter(item -> item.extractionStatus() == AttachmentExtractionStatus.SUCCEEDED)
                .count());
        document.setContentRefreshedAt(
                request.retrievedAt() == null ? LocalDateTime.now() : request.retrievedAt()
        );

        PolicyDocument saved = policyDocumentRepository.save(document);
        attachmentPersistenceService.upsert(saved.getId(), attachments);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<PolicyDocument> listRecentPolicies() {
        return policyDocumentRepository.findTop20ByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<PolicyDocument> listPoliciesBySearchTaskId(Long taskId) {
        return policyDocumentRepository.findBySearchTaskIdOrderByPublishDateDescCreatedAtDesc(taskId);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
