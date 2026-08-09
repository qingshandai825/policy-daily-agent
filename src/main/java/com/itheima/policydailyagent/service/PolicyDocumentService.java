package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.dto.PolicyDocumentCreateRequest;
import com.itheima.policydailyagent.entity.PolicyDocument;
import com.itheima.policydailyagent.repository.PolicyDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PolicyDocumentService {

    private final PolicyDocumentRepository policyDocumentRepository;

    public PolicyDocumentService(PolicyDocumentRepository policyDocumentRepository) {
        this.policyDocumentRepository = policyDocumentRepository;
    }

    @Transactional
    public PolicyDocument createPolicyDocument(PolicyDocumentCreateRequest request) {
        if (policyDocumentRepository.existsBySourceUrl(request.sourceUrl())) {
            throw new IllegalArgumentException("该政策链接已存在，不能重复保存");
        }

        PolicyDocument document = new PolicyDocument();
        document.setTitle(request.title());
        document.setSourceName(request.sourceName());
        document.setPublishDate(request.publishDate());
        document.setSourceUrl(request.sourceUrl());
        document.setContent(request.content());
        document.setCategory(request.category());
        document.setDailyTaskId(request.dailyTaskId());
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
        document.setStatus("RAW");

        return policyDocumentRepository.save(document);
    }

    @Transactional(readOnly = true)
    public List<PolicyDocument> listRecentPolicies() {
        return policyDocumentRepository.findTop20ByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<PolicyDocument> listPoliciesByTaskId(Long taskId) {
        return policyDocumentRepository.findByDailyTaskIdOrderByPublishDateDescCreatedAtDesc(taskId);
    }
}
