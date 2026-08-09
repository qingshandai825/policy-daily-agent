package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.agent.dto.DateFilterToolInput;
import com.itheima.policydailyagent.agent.dto.DeduplicationResult;
import com.itheima.policydailyagent.agent.dto.DeduplicationToolInput;
import com.itheima.policydailyagent.agent.model.AgentStage;
import com.itheima.policydailyagent.agent.service.AgentToolExecutor;
import com.itheima.policydailyagent.agent.tool.AgentTool;
import com.itheima.policydailyagent.agent.tool.AgentToolCall;
import com.itheima.policydailyagent.agent.tool.impl.PolicyCrawlerAgentTool;
import com.itheima.policydailyagent.agent.tool.impl.PolicyDateFilterAgentTool;
import com.itheima.policydailyagent.agent.tool.impl.PolicyDeduplicationAgentTool;
import com.itheima.policydailyagent.agent.tool.impl.PolicySummaryAgentTool;
import com.itheima.policydailyagent.dto.*;
import com.itheima.policydailyagent.entity.DailyTask;
import com.itheima.policydailyagent.entity.PolicyDocument;
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

    private final PolicyDocumentService policyDocumentService;
    private final DailyTaskService dailyTaskService;
    private final AgentToolExecutor agentToolExecutor;
    private final PolicyCrawlerAgentTool crawlerTool;
    private final PolicyDateFilterAgentTool dateFilterTool;
    private final PolicyDeduplicationAgentTool deduplicationTool;
    private final PolicySummaryAgentTool summaryTool;

    public PolicyDiscoveryService(
            PolicyDocumentService policyDocumentService,
            DailyTaskService dailyTaskService,
            AgentToolExecutor agentToolExecutor,
            PolicyCrawlerAgentTool crawlerTool,
            PolicyDateFilterAgentTool dateFilterTool,
            PolicyDeduplicationAgentTool deduplicationTool,
            PolicySummaryAgentTool summaryTool
    ) {
        this.policyDocumentService = policyDocumentService;
        this.dailyTaskService = dailyTaskService;
        this.agentToolExecutor = agentToolExecutor;
        this.crawlerTool = crawlerTool;
        this.dateFilterTool = dateFilterTool;
        this.deduplicationTool = deduplicationTool;
        this.summaryTool = summaryTool;
    }

    public PolicyDiscoverResult discoverAndSave(PolicyDiscoverRequest request) {
        return discoverAndSave(request, null);
    }

    public PolicyDiscoverResult discoverAndSave(PolicyDiscoverRequest request, Long agentRunId) {
        if (request == null || !hasText(request.listPageUrl())) {
            throw new IllegalArgumentException("政策栏目页链接不能为空");
        }

        if (request.resolvedTargetStartDate() != null
                && request.resolvedTargetEndDate() != null
                && request.resolvedTargetStartDate().isAfter(request.resolvedTargetEndDate())) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }

        List<String> keywords = normalizeKeywords(request.keywords());
        int maxLinks = request.maxLinks() == null || request.maxLinks() <= 0
                ? DEFAULT_MAX_LINKS
                : request.maxLinks();

        boolean autoSummarize = Boolean.TRUE.equals(request.autoSummarize());
        DailyTask dailyTask = dailyTaskService.startOrReuse(request, keywords);

        List<CandidateLink> candidateLinks = discoverLinks(
                request.listPageUrl(),
                keywords,
                maxLinks
        );

        int savedCount = 0;
        int duplicateCount = 0;
        int filteredCount = 0;
        int failedCount = 0;
        int summarizedCount = 0;

        List<String> savedTitles = new ArrayList<>();
        List<String> duplicateUrls = new ArrayList<>();
        List<String> filteredMessages = new ArrayList<>();
        List<String> failedMessages = new ArrayList<>();

        for (CandidateLink candidateLink : candidateLinks) {
            String url = candidateLink.url();

            try {
                DeduplicationResult urlDuplicate = executeTool(
                        agentRunId,
                        dailyTask.getId(),
                        AgentStage.DEDUPLICATION,
                        deduplicationTool,
                        new DeduplicationToolInput(url, null)
                );
                if (urlDuplicate.duplicate()) {
                    duplicateCount++;
                    duplicateUrls.add(url);
                    continue;
                }

                PolicyCrawlResult crawlResult = executeTool(
                        agentRunId,
                        dailyTask.getId(),
                        AgentStage.CRAWLING,
                        crawlerTool,
                        url
                );
                DateFilterResult filterResult = executeTool(
                        agentRunId,
                        dailyTask.getId(),
                        AgentStage.DATE_FILTERING,
                        dateFilterTool,
                        new DateFilterToolInput(
                                crawlResult,
                                request.resolvedTargetStartDate(),
                                request.resolvedTargetEndDate()
                        )
                );

                if (!filterResult.accepted()) {
                    filteredCount++;
                    filteredMessages.add(url + " filtered: " + filterResult.reason());
                    continue;
                }

                DeduplicationResult contentDuplicate = executeTool(
                        agentRunId,
                        dailyTask.getId(),
                        AgentStage.DEDUPLICATION,
                        deduplicationTool,
                        new DeduplicationToolInput(null, crawlResult.contentHash())
                );
                if (contentDuplicate.duplicate()) {
                    duplicateCount++;
                    duplicateUrls.add(url + " duplicate content hash");
                    continue;
                }

                PolicyDocumentCreateRequest createRequest = new PolicyDocumentCreateRequest(
                        crawlResult.title(),
                        crawlResult.sourceName(),
                        crawlResult.publishDate(),
                        crawlResult.sourceUrl(),
                        crawlResult.content(),
                        inferCategory(crawlResult.sourceName(), crawlResult.sourceUrl()),
                        dailyTask.getId(),
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
                        filterResult.reason()
                );

                PolicyDocument savedDocument = policyDocumentService.createPolicyDocument(createRequest);

                savedCount++;
                savedTitles.add(savedDocument.getTitle());

                if (autoSummarize) {
                    executeTool(
                            agentRunId,
                            dailyTask.getId(),
                            AgentStage.SUMMARIZATION,
                            summaryTool,
                            savedDocument.getId()
                    );
                    summarizedCount++;
                }

            } catch (Exception e) {
                failedCount++;
                failedMessages.add(url + " 抓取失败：" + e.getMessage());
            }
        }

        dailyTaskService.complete(
                dailyTask.getId(),
                candidateLinks.size(),
                savedCount,
                duplicateCount,
                filteredCount,
                failedCount,
                summarizedCount
        );

        return new PolicyDiscoverResult(
                dailyTask.getId(),
                candidateLinks.size(),
                savedCount,
                duplicateCount,
                filteredCount,
                failedCount,
                summarizedCount,
                savedTitles,
                duplicateUrls,
                filteredMessages,
                failedMessages
        );
    }

    private <I, O> O executeTool(
            Long agentRunId,
            Long taskId,
            AgentStage stage,
            AgentTool<I, O> tool,
            I input
    ) {
        if (agentRunId == null) {
            return tool.execute(input);
        }
        return agentToolExecutor.execute(
                new AgentToolCall(agentRunId, taskId, stage),
                tool,
                input
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
        if (inputKeywords == null || inputKeywords.isEmpty()) {
            return List.of();
        }
        return inputKeywords.stream()
                .filter(this::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private boolean matchesKeywords(String text, List<String> keywords) {
        if (!hasText(text)) {
            return false;
        }
        if (keywords == null || keywords.isEmpty()) {
            return true;
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
