package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.dto.*;
import com.itheima.policydailyagent.domain.search.SearchTask;
import com.itheima.policydailyagent.domain.search.SearchTaskPolicy;
import com.itheima.policydailyagent.entity.PolicyDocument;
import com.itheima.policydailyagent.repository.PolicyDocumentRepository;
import com.itheima.policydailyagent.repository.SearchTaskPolicyRepository;
import com.itheima.policydailyagent.dto.PolicyLinkPreviewResult;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;


import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PolicyDiscoveryService {

    private static final int DEFAULT_MAX_LINKS = 10;

    private final PolicyCrawlerService policyCrawlerService;
    private final PolicyDocumentService policyDocumentService;
    private final PolicyDocumentRepository policyDocumentRepository;
    private final SearchTaskPolicyRepository searchTaskPolicyRepository;
    private final SearchTaskService searchTaskService;
    private final PolicyDateFilterService policyDateFilterService;

    public PolicyDiscoveryService(
            PolicyCrawlerService policyCrawlerService,
            PolicyDocumentService policyDocumentService,
            PolicyDocumentRepository policyDocumentRepository,
            SearchTaskPolicyRepository searchTaskPolicyRepository,
            SearchTaskService searchTaskService,
            PolicyDateFilterService policyDateFilterService
    ) {
        this.policyCrawlerService = policyCrawlerService;
        this.policyDocumentService = policyDocumentService;
        this.policyDocumentRepository = policyDocumentRepository;
        this.searchTaskPolicyRepository = searchTaskPolicyRepository;
        this.searchTaskService = searchTaskService;
        this.policyDateFilterService = policyDateFilterService;
    }

    public PolicyDiscoverResult discoverAndSave(PolicyDiscoverRequest request) {
        if (request == null || !hasText(request.listPageUrl())) {
            throw new IllegalArgumentException("政策栏目页链接不能为空");
        }

        if (request.resolvedTargetStartDate().isAfter(request.resolvedTargetEndDate())) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期" );
        }

        List<String> keywords = normalizeKeywords(request.keywords());
        int maxLinks = request.maxLinks() == null || request.maxLinks() <= 0
                ? DEFAULT_MAX_LINKS
                : request.maxLinks();

        SearchTask searchTask = searchTaskService.createAndStart(request, keywords);

        List<CandidateLink> candidateLinks;
        try {
            candidateLinks = discoverLinks(
                    request.listPageUrl(),
                    keywords,
                    maxLinks
            );
        } catch (RuntimeException e) {
            searchTaskService.fail(searchTask.getId(), 0, 1, e.getMessage());
            throw e;
        }

        int savedCount = 0;
        int duplicateCount = 0;
        int filteredCount = 0;
        int failedCount = 0;

        List<String> savedTitles = new ArrayList<>();
        List<String> duplicateUrls = new ArrayList<>();
        List<String> filteredMessages = new ArrayList<>();
        List<String> failedMessages = new ArrayList<>();

        int discoveryOrder = 0;
        for (CandidateLink candidateLink : candidateLinks) {
            discoveryOrder++;
            String url = candidateLink.url();

            try {
                var existingByUrl = policyDocumentRepository.findBySourceUrl(url);
                if (existingByUrl.isPresent()) {
                    associate(searchTask, candidateLink, existingByUrl.get(), discoveryOrder);
                    duplicateCount++;
                    duplicateUrls.add(url);
                    continue;
                }

                PolicyCrawlResult crawlResult = policyCrawlerService.crawl(url);
                DateFilterResult filterResult = policyDateFilterService.filter(
                        crawlResult,
                        request.resolvedTargetStartDate(),
                        request.resolvedTargetEndDate()
                );

                if (!filterResult.accepted()) {
                    filteredCount++;
                    filteredMessages.add(url + " filtered: " + filterResult.reason());
                    continue;
                }

                if (hasText(crawlResult.contentHash())) {
                    var existingByHash = policyDocumentRepository.findFirstByContentHash(crawlResult.contentHash());
                    if (existingByHash.isPresent()) {
                        associate(searchTask, candidateLink, existingByHash.get(), discoveryOrder);
                        duplicateCount++;
                        duplicateUrls.add(url + " duplicate content hash");
                        continue;
                    }
                }

                PolicyDocumentCreateRequest createRequest = new PolicyDocumentCreateRequest(
                        crawlResult.title(),
                        crawlResult.sourceName(),
                        crawlResult.publishDate(),
                        crawlResult.sourceUrl(),
                        crawlResult.content(),
                        inferCategory(crawlResult.sourceName(), crawlResult.sourceUrl()),
                        searchTask.getId(),
                        crawlResult.retrievedAt(),
                        crawlResult.sourceDomain(),
                        crawlResult.sourceType(),
                        crawlResult.authorityLevel(),
                        crawlResult.dateSource(),
                        crawlResult.dateText(),
                        crawlResult.dateConfidence(),
                        crawlResult.contentHash(),
                        crawlResult.evidenceSnippet(),
                        filterResult.status(),
                        filterResult.reason(),
                        crawlResult.cleanedContent(),
                        crawlResult.contentCompleteness(),
                        crawlResult.contentQualityReason(),
                        crawlResult.attachments()
                );

                PolicyDocument savedDocument = policyDocumentService.createPolicyDocument(createRequest);
                associate(searchTask, candidateLink, savedDocument, discoveryOrder);

                savedCount++;
                savedTitles.add(savedDocument.getTitle());

            } catch (Exception e) {
                failedCount++;
                failedMessages.add(url + " 抓取失败：" + e.getMessage());
            }
        }

        searchTaskService.complete(
                searchTask.getId(),
                candidateLinks.size(),
                savedCount,
                duplicateCount,
                filteredCount,
                failedCount
        );

        return new PolicyDiscoverResult(
                searchTask.getId(),
                candidateLinks.size(),
                savedCount,
                duplicateCount,
                filteredCount,
                failedCount,
                savedTitles,
                duplicateUrls,
                filteredMessages,
                failedMessages
        );
    }

    private List<CandidateLink> discoverLinks(
            String listPageUrl,
            List<String> keywords,
            int maxLinks
    ) {
        try {
            Document document = Jsoup.connect(listPageUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36")
                    .referrer("https://www.baidu.com/")
                    .timeout(15000)
                    .followRedirects(true)
                    .maxBodySize(0)
                    .get();

            Elements links = document.select("a[href]");

            Map<String, CandidateLink> candidates = new LinkedHashMap<>();

            for (Element link : links) {
                String href = link.attr("href");
                String text = cleanText(link.text());

                String absoluteUrl = link.absUrl("href");

                if (!hasText(absoluteUrl)) {
                    absoluteUrl = resolveUrl(listPageUrl, href);
                }

                absoluteUrl = cleanUrl(absoluteUrl);

                if (!hasText(absoluteUrl)) {
                    continue;
                }

                if (!isLikelyArticleUrl(absoluteUrl)) {
                    continue;
                }

                if (!matchesKeywords(text + " " + absoluteUrl, keywords)) {
                    continue;
                }

                candidates.putIfAbsent(absoluteUrl, new CandidateLink(absoluteUrl, text));

                if (candidates.size() >= maxLinks) {
                    break;
                }
            }

            return new ArrayList<>(candidates.values());

        } catch (Exception e) {
            throw new RuntimeException("政策栏目页解析失败：" + e.getMessage(), e);
        }
    }

    private List<String> normalizeKeywords(List<String> inputKeywords) {
        if (inputKeywords != null && !inputKeywords.isEmpty()) {
            return inputKeywords.stream()
                    .filter(this::hasText)
                    .map(String::trim)
                    .toList();
        }

        return List.of(
                "人工智能",
                "智能制造",
                "制造业",
                "工业互联网",
                "数据集",
                "工业数据",
                "大模型",
                "智能体",
                "算力",
                "数字化",
                "人工智能+制造",
                "模数共振"
        );
    }

    private boolean matchesKeywords(String text, List<String> keywords) {
        if (!hasText(text)) {
            return false;
        }

        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    private boolean isLikelyArticleUrl(String url) {
        if (!hasText(url)) {
            return false;
        }

        String lower = url.toLowerCase();

        if (lower.contains("javascript:")) {
            return false;
        }

        if (lower.endsWith(".pdf")
                || lower.endsWith(".doc")
                || lower.endsWith(".docx")
                || lower.endsWith(".xls")
                || lower.endsWith(".xlsx")
                || lower.endsWith(".zip")
                || lower.endsWith(".rar")) {
            return false;
        }

        if (lower.contains("index.html")) {
            return false;
        }

        return lower.endsWith(".html")
                || lower.contains("/art/")
                || lower.contains("/content_")
                || lower.contains("/t")
                || lower.contains("info");
    }

    private String inferCategory(String sourceName, String sourceUrl) {
        String source = safe(sourceName);
        String url = safe(sourceUrl);

        if (source.contains("山东")
                || url.contains("shandong.gov.cn")
                || url.contains("gxt.shandong.gov.cn")) {
            return "省内工作推进";
        }

        if (source.contains("国务院")
                || source.contains("工业和信息化部")
                || source.contains("国家发展改革委")
                || source.contains("国家数据局")
                || source.contains("国家网信办")
                || url.contains("miit.gov.cn")
                || url.contains("ndrc.gov.cn")
                || url.contains("nda.gov.cn")
                || url.contains("cac.gov.cn")) {
            return "国家重点事项";
        }

        return "政策文件";
    }

    private String resolveUrl(String baseUrl, String href) {
        try {
            if (!hasText(href)) {
                return "";
            }

            URI baseUri = URI.create(baseUrl);
            return baseUri.resolve(href).toString();

        } catch (Exception e) {
            return "";
        }
    }

    private String cleanUrl(String url) {
        if (!hasText(url)) {
            return "";
        }

        int index = url.indexOf("#");
        if (index >= 0) {
            url = url.substring(0, index);
        }

        return url.trim();
    }

    private String cleanText(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace("\u00A0", " ")
                .replace("　", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void associate(
            SearchTask searchTask,
            CandidateLink candidateLink,
            PolicyDocument document,
            int discoveryOrder
    ) {
        if (searchTaskPolicyRepository.existsBySearchTaskIdAndPolicyId(searchTask.getId(), document.getId())) {
            return;
        }
        SearchTaskPolicy association = new SearchTaskPolicy();
        association.setSearchTaskId(searchTask.getId());
        association.setPolicyId(document.getId());
        association.setProvider("DIRECT_LIST");
        association.setDiscoveredUrl(candidateLink.url());
        association.setDiscoveredTitle(candidateLink.text());
        association.setDiscoveryOrder(discoveryOrder);
        searchTaskPolicyRepository.save(association);
    }
    private record CandidateLink(String url, String text) {
    }

    public PolicyLinkPreviewResult previewLinks(String listPageUrl) {
        if (!hasText(listPageUrl)) {
            throw new IllegalArgumentException("政策栏目页链接不能为空");
        }

        try {
            Document document = Jsoup.connect(listPageUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36")
                    .referrer("https://www.baidu.com/")
                    .timeout(15000)
                    .followRedirects(true)
                    .maxBodySize(0)
                    .get();

            Elements links = document.select("a[href]");

            List<PolicyLinkPreviewResult.LinkItem> result = new ArrayList<>();

            for (Element link : links) {
                String text = cleanText(link.text());
                String absoluteUrl = link.absUrl("href");

                if (!hasText(absoluteUrl)) {
                    absoluteUrl = resolveUrl(listPageUrl, link.attr("href"));
                }

                absoluteUrl = cleanUrl(absoluteUrl);

                if (!hasText(absoluteUrl)) {
                    continue;
                }

                result.add(new PolicyLinkPreviewResult.LinkItem(text, absoluteUrl));
            }

            return new PolicyLinkPreviewResult(result.size(), result);

        } catch (Exception e) {
            throw new RuntimeException("政策栏目页链接预览失败：" + e.getMessage(), e);
        }
    }
}
