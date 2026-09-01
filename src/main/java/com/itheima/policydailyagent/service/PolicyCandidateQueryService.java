package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.domain.policy.PolicyReviewStatus;
import com.itheima.policydailyagent.domain.search.SearchTaskPolicy;
import com.itheima.policydailyagent.dto.PolicyCandidateView;
import com.itheima.policydailyagent.dto.ReviewTaskSummary;
import com.itheima.policydailyagent.entity.PolicyDocument;
import com.itheima.policydailyagent.repository.PolicyDocumentRepository;
import com.itheima.policydailyagent.repository.SearchTaskPolicyRepository;
import com.itheima.policydailyagent.repository.SearchTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PolicyCandidateQueryService {

    private final SearchTaskRepository searchTaskRepository;
    private final SearchTaskPolicyRepository associationRepository;
    private final PolicyDocumentRepository documentRepository;

    public PolicyCandidateQueryService(
            SearchTaskRepository searchTaskRepository,
            SearchTaskPolicyRepository associationRepository,
            PolicyDocumentRepository documentRepository
    ) {
        this.searchTaskRepository = searchTaskRepository;
        this.associationRepository = associationRepository;
        this.documentRepository = documentRepository;
    }

    @Transactional(readOnly = true)
    public List<PolicyCandidateView> list(
            Long searchTaskId,
            PolicyReviewStatus status,
            String keyword,
            String sourceId
    ) {
        requireTask(searchTaskId);
        List<SearchTaskPolicy> associations = associationRepository
                .findBySearchTaskIdOrderByDiscoveryOrderAscIdAsc(searchTaskId);
        Map<Long, PolicyDocument> documents = loadDocuments(associations);
        String normalizedKeyword = safe(keyword).toLowerCase(Locale.ROOT);
        String normalizedSourceId = safe(sourceId);

        return associations.stream()
                .filter(association -> !hasText(normalizedSourceId)
                        || normalizedSourceId.equals(association.getSourceId()))
                .filter(association -> documents.containsKey(association.getPolicyId()))
                .map(association -> toView(association, documents.get(association.getPolicyId()), false))
                .filter(view -> status == null || status == view.reviewStatus())
                .filter(view -> !hasText(normalizedKeyword) || matchesKeyword(view, normalizedKeyword))
                .toList();
    }

    @Transactional(readOnly = true)
    public PolicyCandidateView get(Long searchTaskId, Long policyId) {
        requireTask(searchTaskId);
        SearchTaskPolicy association = associationRepository
                .findBySearchTaskIdAndPolicyId(searchTaskId, policyId)
                .orElseThrow(() -> new IllegalArgumentException("该政策不属于指定搜索任务"));
        PolicyDocument document = documentRepository.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("政策文档不存在，id=" + policyId));
        return toView(association, document, true);
    }

    @Transactional(readOnly = true)
    public ReviewTaskSummary summarize(Long searchTaskId) {
        List<PolicyCandidateView> policies = list(searchTaskId, null, null, null);
        return new ReviewTaskSummary(
                searchTaskId,
                policies.size(),
                count(policies, PolicyReviewStatus.PENDING),
                count(policies, PolicyReviewStatus.ACCEPTED),
                count(policies, PolicyReviewStatus.REJECTED),
                count(policies, PolicyReviewStatus.DEFERRED)
        );
    }

    private Map<Long, PolicyDocument> loadDocuments(List<SearchTaskPolicy> associations) {
        List<Long> ids = associations.stream().map(SearchTaskPolicy::getPolicyId).distinct().toList();
        Map<Long, PolicyDocument> documents = new LinkedHashMap<>();
        documentRepository.findAllById(ids).forEach(document -> documents.put(document.getId(), document));
        return documents;
    }

    private PolicyCandidateView toView(
            SearchTaskPolicy association,
            PolicyDocument document,
            boolean includeContent
    ) {
        return new PolicyCandidateView(
                document.getId(),
                association.getSearchTaskId(),
                association.getSourceId(),
                association.getProvider(),
                document.getTitle(),
                document.getSourceName(),
                document.getPublishDate(),
                document.getSourceUrl(),
                association.getDiscoveredUrl(),
                document.getCategory(),
                document.getPolicyType(),
                document.getKeywords(),
                shortSummary(association, document),
                includeContent ? document.getCleanedContent() : null,
                document.getEvidenceSnippet(),
                document.getContentCompleteness(),
                document.getContentQualityReason(),
                document.getAttachmentCount(),
                document.getExtractedAttachmentCount(),
                document.getSourceDomain(),
                document.getAuthorityLevel(),
                document.getFilterStatus(),
                document.getRelevanceScore(),
                document.getReviewStatus(),
                document.getAnalysisStatus(),
                document.getReviewComment(),
                document.getReviewedBy(),
                document.getReviewedAt()
        );
    }

    private String shortSummary(SearchTaskPolicy association, PolicyDocument document) {
        String value = hasText(association.getSearchSnippet())
                ? association.getSearchSnippet()
                : document.getEvidenceSnippet();
        if (!hasText(value)) {
            value = document.getSummary();
        }
        value = safe(value);
        return value.length() <= 260 ? value : value.substring(0, 260) + "…";
    }

    private boolean matchesKeyword(PolicyCandidateView view, String keyword) {
        String searchable = String.join(" ",
                safe(view.title()),
                safe(view.sourceName()),
                safe(view.category()),
                safe(view.policyType()),
                safe(view.keywords()),
                safe(view.shortSummary())
        ).toLowerCase(Locale.ROOT);
        return searchable.contains(keyword);
    }

    private long count(List<PolicyCandidateView> policies, PolicyReviewStatus status) {
        return policies.stream().filter(policy -> policy.reviewStatus() == status).count();
    }

    private void requireTask(Long taskId) {
        if (taskId == null || !searchTaskRepository.existsById(taskId)) {
            throw new IllegalArgumentException("搜索任务不存在，id=" + taskId);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
