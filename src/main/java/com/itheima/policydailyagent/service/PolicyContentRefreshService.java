package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.domain.policy.AttachmentExtractionStatus;
import com.itheima.policydailyagent.domain.policy.PolicyAnalysisStatus;
import com.itheima.policydailyagent.domain.policy.PolicyReviewStatus;
import com.itheima.policydailyagent.dto.PolicyCrawlResult;
import com.itheima.policydailyagent.entity.PolicyDocument;
import com.itheima.policydailyagent.repository.PolicyDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class PolicyContentRefreshService {

    private final PolicyDocumentRepository policyRepository;
    private final PolicyCrawlerService crawlerService;
    private final PolicyAttachmentPersistenceService attachmentPersistenceService;

    public PolicyContentRefreshService(
            PolicyDocumentRepository policyRepository,
            PolicyCrawlerService crawlerService,
            PolicyAttachmentPersistenceService attachmentPersistenceService
    ) {
        this.policyRepository = policyRepository;
        this.crawlerService = crawlerService;
        this.attachmentPersistenceService = attachmentPersistenceService;
    }

    @Transactional
    public PolicyDocument refreshAccepted(Long policyId) {
        PolicyDocument policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("政策文档不存在，id=" + policyId));
        if (policy.getReviewStatus() != PolicyReviewStatus.ACCEPTED) {
            throw new IllegalArgumentException("只有已采纳政策允许在分析工作台重新抓取正文");
        }

        String previousHash = policy.getContentHash();
        PolicyCrawlResult crawl = crawlerService.crawl(policy.getSourceUrl());

        policy.setContent(crawl.content());
        policy.setCleanedContent(crawl.cleanedContent());
        policy.setContentHash(crawl.contentHash());
        policy.setEvidenceSnippet(crawl.evidenceSnippet());
        policy.setRetrievedAt(crawl.retrievedAt());
        policy.setContentCompleteness(crawl.contentCompleteness());
        policy.setContentQualityReason(crawl.contentQualityReason());
        policy.setAttachmentCount(crawl.attachments().size());
        policy.setExtractedAttachmentCount((int) crawl.attachments().stream()
                .filter(item -> item.extractionStatus() == AttachmentExtractionStatus.SUCCEEDED)
                .count());
        policy.setContentRefreshedAt(LocalDateTime.now());

        if (policy.getPublishDate() == null && crawl.publishDate() != null) {
            policy.setPublishDate(crawl.publishDate());
        }
        if (!Objects.equals(previousHash, crawl.contentHash())) {
            policy.setAnalysisStatus(PolicyAnalysisStatus.READY);
        }

        PolicyDocument saved = policyRepository.save(policy);
        attachmentPersistenceService.upsert(saved.getId(), crawl.attachments());
        return saved;
    }
}
