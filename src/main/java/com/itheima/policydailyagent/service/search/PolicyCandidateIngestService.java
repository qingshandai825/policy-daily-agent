package com.itheima.policydailyagent.service.search;

import com.itheima.policydailyagent.config.PolicySourceProperties;
import com.itheima.policydailyagent.domain.search.SearchTask;
import com.itheima.policydailyagent.domain.search.SearchTaskPolicy;
import com.itheima.policydailyagent.dto.DateFilterResult;
import com.itheima.policydailyagent.dto.PolicyCrawlResult;
import com.itheima.policydailyagent.dto.PolicyDocumentCreateRequest;
import com.itheima.policydailyagent.entity.PolicyDocument;
import com.itheima.policydailyagent.repository.PolicyDocumentRepository;
import com.itheima.policydailyagent.repository.SearchTaskPolicyRepository;
import com.itheima.policydailyagent.service.PolicyCrawlerService;
import com.itheima.policydailyagent.service.PolicyDateFilterService;
import com.itheima.policydailyagent.service.PolicyDocumentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class PolicyCandidateIngestService {

    private final PolicyCrawlerService crawlerService;
    private final PolicyDateFilterService dateFilterService;
    private final PolicyDocumentService documentService;
    private final PolicyDocumentRepository documentRepository;
    private final SearchTaskPolicyRepository associationRepository;

    public PolicyCandidateIngestService(
            PolicyCrawlerService crawlerService,
            PolicyDateFilterService dateFilterService,
            PolicyDocumentService documentService,
            PolicyDocumentRepository documentRepository,
            SearchTaskPolicyRepository associationRepository
    ) {
        this.crawlerService = crawlerService;
        this.dateFilterService = dateFilterService;
        this.documentService = documentService;
        this.documentRepository = documentRepository;
        this.associationRepository = associationRepository;
    }

    @Transactional
    public IngestOutcome ingest(
            SearchTask task,
            PolicySourceProperties.Item source,
            PolicySiteAdapter.DiscoveredPolicyLink link,
            int discoveryOrder
    ) {
        Optional<PolicyDocument> sameUrl = documentRepository.findBySourceUrl(link.url());
        if (sameUrl.isPresent()) {
            boolean associated = associate(task, source, link, sameUrl.get(), discoveryOrder);
            return new IngestOutcome(ResultType.DUPLICATE, sameUrl.get().getId(), associated, "链接已存在，已关联当前任务");
        }

        PolicyCrawlResult crawl = crawlerService.crawl(link.url());
        DateFilterResult filter = dateFilterService.filter(
                crawl,
                task.getTargetStartDate(),
                task.getTargetEndDate()
        );
        if (!filter.accepted()) {
            return new IngestOutcome(ResultType.FILTERED, null, false, filter.reason());
        }

        if (hasText(crawl.contentHash())) {
            Optional<PolicyDocument> sameContent = documentRepository.findFirstByContentHash(crawl.contentHash());
            if (sameContent.isPresent()) {
                boolean associated = associate(task, source, link, sameContent.get(), discoveryOrder);
                return new IngestOutcome(ResultType.DUPLICATE, sameContent.get().getId(), associated, "正文重复，已关联规范政策");
            }
        }

        PolicyDocumentCreateRequest request = new PolicyDocumentCreateRequest(
                crawl.title(),
                crawl.sourceName(),
                crawl.publishDate(),
                crawl.sourceUrl(),
                crawl.content(),
                inferCategory(crawl.sourceName(), crawl.sourceUrl()),
                task.getId(),
                crawl.retrievedAt(),
                crawl.sourceDomain(),
                crawl.sourceType(),
                crawl.authorityLevel(),
                crawl.dateSource(),
                crawl.dateText(),
                crawl.dateConfidence(),
                crawl.contentHash(),
                crawl.evidenceSnippet(),
                filter.status(),
                filter.reason(),
                crawl.cleanedContent(),
                crawl.contentCompleteness(),
                crawl.contentQualityReason(),
                crawl.attachments()
        );

        PolicyDocument saved = documentService.createPolicyDocument(request);
        List<String> matchedKeywords = matchedKeywords(task.getKeywords(), saved.getTitle(), saved.getContent());
        saved.setPolicyType(inferPolicyType(saved.getTitle()));
        saved.setKeywords(String.join(",", matchedKeywords));
        saved.setRelevanceScore(calculateRelevance(saved, matchedKeywords));
        saved = documentRepository.save(saved);

        boolean associated = associate(task, source, link, saved, discoveryOrder);
        return new IngestOutcome(ResultType.SAVED, saved.getId(), associated, "候选政策已入库");
    }

    private boolean associate(
            SearchTask task,
            PolicySourceProperties.Item source,
            PolicySiteAdapter.DiscoveredPolicyLink link,
            PolicyDocument document,
            int discoveryOrder
    ) {
        if (associationRepository.existsBySearchTaskIdAndPolicyId(task.getId(), document.getId())) {
            return false;
        }
        SearchTaskPolicy association = new SearchTaskPolicy();
        association.setSearchTaskId(task.getId());
        association.setPolicyId(document.getId());
        association.setSourceId(source == null ? null : source.getId());
        association.setProvider("FIXED_SOURCE");
        association.setDiscoveredUrl(link.url());
        association.setDiscoveredTitle(link.title());
        association.setSearchSnippet(link.snippet());
        association.setDiscoveryOrder(discoveryOrder);
        associationRepository.save(association);
        return true;
    }

    private List<String> matchedKeywords(String configured, String title, String content) {
        String text = (safe(title) + " " + safe(content)).toLowerCase(Locale.ROOT);
        List<String> matched = new ArrayList<>();
        if (!hasText(configured)) {
            return matched;
        }
        for (String keyword : configured.split(",")) {
            String normalized = keyword.trim();
            if (hasText(normalized) && text.contains(normalized.toLowerCase(Locale.ROOT))) {
                matched.add(normalized);
            }
        }
        return matched.stream().distinct().toList();
    }

    private BigDecimal calculateRelevance(PolicyDocument document, List<String> matchedKeywords) {
        int score = 25 + Math.min(matchedKeywords.size() * 12, 60);
        if (safe(document.getAuthorityLevel()).contains("AUTHORITY")) {
            score += 10;
        }
        return BigDecimal.valueOf(Math.min(score, 100));
    }

    private String inferCategory(String sourceName, String sourceUrl) {
        String text = safe(sourceName) + " " + safe(sourceUrl);
        return text.contains("山东") ? "省内工作推进" : "国家重点事项";
    }

    private String inferPolicyType(String title) {
        String value = safe(title);
        for (String type : List.of("通知", "意见", "实施方案", "行动计划", "公告", "办法", "规划", "工作动态")) {
            if (value.contains(type)) {
                return type;
            }
        }
        return "政策材料";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public enum ResultType {
        SAVED,
        DUPLICATE,
        FILTERED
    }

    public record IngestOutcome(
            ResultType type,
            Long policyId,
            boolean associationCreated,
            String message
    ) {
    }
}
