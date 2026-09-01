package com.itheima.policydailyagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itheima.policydailyagent.dto.DateFilterResult;
import com.itheima.policydailyagent.dto.PolicyCrawlResult;
import com.itheima.policydailyagent.dto.PolicyDocumentCreateRequest;
import com.itheima.policydailyagent.dto.PolicySearchResult;
import com.itheima.policydailyagent.dto.PolicySearchResult.SearchItem;
import com.itheima.policydailyagent.entity.PolicyDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDate;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class QianfanAiSearchService {

    @Value("${qianfan.ai-search.endpoint}")
    private String endpoint;

    @Value("${qianfan.ai-search.api-key}")
    private String apiKey;

    @Value("${qianfan.ai-search.model:ernie-4.5-turbo-128k}")
    private String model;

    @Value("${qianfan.ai-search.max-results:10}")
    private Integer defaultMaxResults;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile HttpClient httpClient;

    private final PolicyCrawlerService policyCrawlerService;
    private final PolicyDocumentService policyDocumentService;
    private final PolicyDateFilterService policyDateFilterService;

    public QianfanAiSearchService(
            PolicyCrawlerService policyCrawlerService,
            PolicyDocumentService policyDocumentService,
            PolicyDateFilterService policyDateFilterService
    ) {
        this.policyCrawlerService = policyCrawlerService;
        this.policyDocumentService = policyDocumentService;
        this.policyDateFilterService = policyDateFilterService;
    }

    /**
     * 搜索预览：只返回搜索结果，不入库。
     */
    public PolicySearchResult searchPreview(
            String query,
            List<String> sites,
            Integer maxResults
    ) {
        if (!hasText(query)) {
            throw new IllegalArgumentException("搜索关键词不能为空");
        }

        int limit = maxResults == null || maxResults <= 0 ? defaultMaxResults : maxResults;

        try {
            String prompt = buildSearchPrompt(query, sites, limit);
            String requestBody = buildRequestBody(prompt, sites, limit);

            HttpResponse<String> response = callQianfan(requestBody);

            List<SearchItem> items = parseSearchItemsFromReferences(response.body(), limit);

            // 如果 references 没有解析到结果，再尝试从模型正文中兜底提取。
            // 兜底结果仍然会经过 URL 规则过滤，但不保证完全可靠。
            if (items.isEmpty()) {
                String content = extractAssistantContent(response.body());
                items = parseSearchItemsFromContent(content, limit);
            }

            return new PolicySearchResult(
                    query,
                    items.size(),
                    0,
                    0,
                    0,
                    items,
                    List.of()
            );

        } catch (Exception e) {
            throw new RuntimeException("千帆 AI Search 调用异常：" + e.getMessage(), e);
        }
    }

    /**
     * 搜索并入库：搜索结果 -> 过滤 -> 抓取正文 -> 候选池。搜索阶段不得触发深度分析。
     */
    public PolicySearchResult searchAndSave(
            String query,
            List<String> sites,
            Integer maxResults
    ) {
        return searchAndSave(query, sites, maxResults, null, null);
    }

    public PolicySearchResult searchAndSave(
            String query,
            List<String> sites,
            Integer maxResults,
            LocalDate targetStartDate,
            LocalDate targetEndDate
    ) {
        PolicySearchResult previewResult = searchPreview(query, sites, maxResults);

        int savedCount = 0;
        int duplicateCount = 0;
        int failedCount = 0;

        List<SearchItem> savedItems = new ArrayList<>();
        List<String> failedMessages = new ArrayList<>();

        for (SearchItem item : previewResult.items()) {
            String url = item.url();

            try {
                if (!isPreferredPolicyResult(item)) {
                    failedCount++;
                    failedMessages.add(url + " 已过滤：非优先政策正文页面");
                    continue;
                }

                PolicyCrawlResult crawlResult = policyCrawlerService.crawl(url);

                if (crawlResult == null
                        || !hasText(crawlResult.title())
                        || !hasText(crawlResult.content())
                        || crawlResult.content().length() < 300) {
                    failedCount++;
                    failedMessages.add(url + " 已过滤：未提取到有效政策正文");
                    continue;
                }

                DateFilterResult filterResult = policyDateFilterService.filter(
                        crawlResult,
                        targetStartDate,
                        targetEndDate
                );

                if (!filterResult.accepted()) {
                    failedCount++;
                    failedMessages.add(url + " filtered: " + filterResult.reason());
                    continue;
                }

                PolicyDocumentCreateRequest createRequest = new PolicyDocumentCreateRequest(
                        crawlResult.title(),
                        crawlResult.sourceName(),
                        crawlResult.publishDate(),
                        crawlResult.sourceUrl(),
                        crawlResult.content(),
                        inferCategory(crawlResult.sourceName(), crawlResult.sourceUrl(), crawlResult.title()),
                        null,
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

                savedCount++;
                savedItems.add(item);

            } catch (IllegalArgumentException e) {
                duplicateCount++;
                failedMessages.add(url + " 跳过：" + e.getMessage());
            } catch (Exception e) {
                failedCount++;
                failedMessages.add(url + " 处理失败：" + e.getMessage());
            }
        }

        return new PolicySearchResult(
                query,
                savedItems.size(),
                savedCount,
                duplicateCount,
                failedCount,
                savedItems,
                failedMessages
        );
    }

    private HttpResponse<String> callQianfan(String requestBody) throws Exception {
        if (!hasText(apiKey)) {
            throw new IllegalStateException("千帆 AI Search 未配置；固定信源采集不需要该配置。如需使用千帆，请设置 QIANFAN_API_KEY。");
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient().send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("千帆 AI Search 调用失败，状态码："
                    + response.statusCode()
                    + "，响应："
                    + response.body());
        }

        return response;
    }

    private HttpClient httpClient() {
        HttpClient current = httpClient;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (httpClient == null) {
                httpClient = HttpClient.newHttpClient();
            }
            return httpClient;
        }
    }

    private String buildSearchPrompt(String query, List<String> sites, int maxResults) {
        String siteText = sites == null || sites.isEmpty()
                ? "不限站点"
                : String.join("、", sites);

        return """
                请使用百度搜索检索与以下主题相关的政府政策文件详情页。

                搜索主题：
                %s

                限定站点：
                %s

                必须满足：
                1. 只返回政府官网、部委官网、省级政府官网、工信部门官网中的具体文章详情页。
                2. 链接必须是具体政策、通知、方案、意见、公告、工作动态的详情页。
                3. 不要返回网站首页、栏目页、导航页、搜索页、列表页。
                4. 不要返回视频、图片、百科、问答、论坛、自媒体页面。
                5. 优先返回 URL 中包含 /art/、/zwgk/、/zcwj/、/xxgk/、.html 的页面。
                6. 不要返回仅为域名首页的链接，例如 https://miit.gov.cn/ 或 https://www.miit.gov.cn/。
                7. 严禁根据网站 URL 规律自行编造链接。
                8. 只能返回搜索结果中真实存在、可点击访问的原始链接。
                9. 如果无法确认真实政策详情页链接，请返回空 JSON 数组 []。
                10. 每条结果必须包含真实标题、完整详情页链接、简要摘要。
                11. 最多返回 %d 条。
                12. 必须严格输出 JSON 数组，不要输出 Markdown，不要输出代码块，不要输出解释说明。

                JSON 格式如下：
                [
                  {
                    "title": "政策标题",
                    "url": "https://www.xxx.gov.cn/xxx/xxx.html",
                    "snippet": "摘要"
                  }
                ]
                """.formatted(query, siteText, maxResults);
    }

    private String buildRequestBody(
            String prompt,
            List<String> sites,
            int maxResults
    ) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();

        body.put("model", model);
        body.put("max_completion_tokens", 4096);
        body.put("search_source", "baidu_search_v2");
        body.put("safety_level", "standard");
        body.put("enable_web_page_safety", true);
        body.put("response_format", Map.of("type", "text"));

        // 开发阶段关闭流式，方便 Java 解析
        body.put("stream", false);

        body.put("search_mode", "auto");
        body.put("enable_corner_markers", true);

        body.put("messages", List.of(
                Map.of(
                        "role", "user",
                        "content", prompt
                )
        ));

        body.put("resource_type_filter", List.of(
                Map.of(
                        "type", "web",
                        "top_k", maxResults
                )
        ));

        Map<String, Object> match = new LinkedHashMap<>();

        // 你当前接口要求 site 是 list
        if (sites == null || sites.isEmpty()) {
            match.put("site", List.of());
        } else {
            match.put("site", sites);
        }

        body.put("search_filter", Map.of(
                "match", match
        ));

        return objectMapper.writeValueAsString(body);
    }

    /**
     * 优先从千帆返回的 references 中解析真实搜索引用链接。
     */
    private List<SearchItem> parseSearchItemsFromReferences(String responseBody, int limit) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            JsonNode referencesNode = findReferencesNode(root);

            if (referencesNode == null || !referencesNode.isArray()) {
                return List.of();
            }

            List<SearchItem> items = new ArrayList<>();
            Set<String> seenUrls = new LinkedHashSet<>();

            for (JsonNode node : referencesNode) {
                if (items.size() >= limit) {
                    break;
                }

                String type = firstText(node, "type", "resource_type");
                if (hasText(type) && !"web".equalsIgnoreCase(type)) {
                    continue;
                }

                String title = firstText(node, "title", "web_anchor", "website", "name");
                String url = firstText(node, "url", "link", "href");
                String snippet = firstText(node, "content", "snippet", "summary", "description", "abstract");

                url = cleanUrl(url);

                if (!isValidPolicyDetailUrl(url)) {
                    continue;
                }

                if (!seenUrls.add(url)) {
                    continue;
                }

                items.add(new SearchItem(
                        cleanText(title),
                        url,
                        cleanText(snippet)
                ));
            }

            return items;

        } catch (Exception e) {
            return List.of();
        }
    }

    private JsonNode findReferencesNode(JsonNode root) {
        JsonNode node;

        node = root.at("/choices/0/references");
        if (node != null && !node.isMissingNode() && node.isArray()) {
            return node;
        }

        node = root.at("/choices/0/message/references");
        if (node != null && !node.isMissingNode() && node.isArray()) {
            return node;
        }

        node = root.at("/references");
        if (node != null && !node.isMissingNode() && node.isArray()) {
            return node;
        }

        // 有些返回可能把 references 放在 message 里面的其他字段
        node = root.at("/choices/0/message/search_info/references");
        if (node != null && !node.isMissingNode() && node.isArray()) {
            return node;
        }

        return null;
    }

    /**
     * 如果 references 没有结果，再从模型正文里兜底解析 JSON。
     * 注意：这一步可能解析到模型生成链接，所以后续入库时还会再次 crawl 校验。
     */
    private List<SearchItem> parseSearchItemsFromContent(String content, int limit) {
        if (!hasText(content)) {
            return List.of();
        }

        String jsonText = extractJsonArray(content);

        List<SearchItem> parsedItems = parseJsonArrayItems(jsonText, limit);

        if (!parsedItems.isEmpty()) {
            return parsedItems;
        }

        return fallbackExtractUrls(content, limit);
    }

    private String extractAssistantContent(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        JsonNode contentNode = root.at("/choices/0/message/content");
        if (contentNode != null && !contentNode.isMissingNode() && !contentNode.isNull()) {
            return contentNode.asText();
        }

        JsonNode textNode = root.at("/result");
        if (textNode != null && !textNode.isMissingNode() && !textNode.isNull()) {
            return textNode.asText();
        }

        return responseBody;
    }

    private String extractJsonArray(String content) {
        String cleaned = content
                .replace("```json", "")
                .replace("```JSON", "")
                .replace("```", "")
                .trim();

        int start = cleaned.indexOf("[");
        int end = cleaned.lastIndexOf("]");

        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1);
        }

        return cleaned;
    }

    private List<SearchItem> parseJsonArrayItems(String jsonText, int limit) {
        try {
            JsonNode array = objectMapper.readTree(jsonText);

            if (!array.isArray()) {
                return List.of();
            }

            List<SearchItem> items = new ArrayList<>();
            Set<String> seenUrls = new LinkedHashSet<>();

            for (JsonNode node : array) {
                if (items.size() >= limit) {
                    break;
                }

                String title = firstText(node, "title", "name");
                String url = cleanUrl(firstText(node, "url", "link", "href"));
                String snippet = firstText(node, "snippet", "summary", "description", "abstract");

                if (!isValidPolicyDetailUrl(url)) {
                    continue;
                }

                if (!seenUrls.add(url)) {
                    continue;
                }

                items.add(new SearchItem(
                        cleanText(title),
                        url,
                        cleanText(snippet)
                ));
            }

            return items;

        } catch (Exception e) {
            return List.of();
        }
    }

    private List<SearchItem> fallbackExtractUrls(String content, int limit) {
        List<SearchItem> items = new ArrayList<>();

        Pattern pattern = Pattern.compile("https?://[^\\s，。；、\\]\\)）\"']+");
        Matcher matcher = pattern.matcher(content);

        Set<String> seenUrls = new LinkedHashSet<>();

        while (matcher.find() && items.size() < limit) {
            String url = cleanUrl(matcher.group());

            if (!isValidPolicyDetailUrl(url)) {
                continue;
            }

            if (!seenUrls.add(url)) {
                continue;
            }

            items.add(new SearchItem(
                    "",
                    url,
                    ""
            ));
        }

        return items;
    }

    /**
     * 只做 URL 形态过滤，不做访问校验。
     * 真正入库前会通过 PolicyCrawlerService.crawl(url) 校验正文是否可提取。
     */
    private boolean isValidPolicyDetailUrl(String url) {
        if (!hasText(url)) {
            return false;
        }

        String lower = url.toLowerCase();

        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return false;
        }

        // 过滤首页
        if (lower.matches("^https?://(www\\.)?miit\\.gov\\.cn/?$")
                || lower.matches("^https?://(www\\.)?ndrc\\.gov\\.cn/?$")
                || lower.matches("^https?://(www\\.)?nda\\.gov\\.cn/?$")
                || lower.matches("^https?://(www\\.)?shandong\\.gov\\.cn/?$")
                || lower.matches("^https?://(www\\.)?gxt\\.shandong\\.gov\\.cn/?$")) {
            return false;
        }

        // 过滤明显栏目页
        if (lower.endsWith("/index.html")
                || lower.endsWith("/index.htm")
                || lower.contains("/index_")) {
            return false;
        }

        // 过滤附件
        if (lower.endsWith(".pdf")
                || lower.endsWith(".doc")
                || lower.endsWith(".docx")
                || lower.endsWith(".xls")
                || lower.endsWith(".xlsx")
                || lower.endsWith(".zip")
                || lower.endsWith(".rar")) {
            return false;
        }

        return lower.contains("/art/")
                || lower.contains("/content_")
                || lower.contains("/t20")
                || lower.endsWith(".html")
                || lower.endsWith(".htm");
    }

    /**
     * 搜索结果入库前的优先级过滤。
     */
    private boolean isPreferredPolicyResult(SearchItem item) {
        if (item == null || !hasText(item.url())) {
            return false;
        }

        String title = safe(item.title());
        String url = safe(item.url()).toLowerCase();
        String snippet = safe(item.snippet());
        String text = title + " " + snippet;

        // 过滤解读、图解、权责清单、行政检查等非正文政策文件
        if (title.contains("一图读懂")
                || title.contains("图解")
                || title.contains("解读")
                || title.contains("权责清单")
                || title.contains("行政检查")
                || url.contains("/zcjd/")
                || url.contains("/qzqd/")) {
            return false;
        }

        boolean titleLooksLikePolicy = title.contains("通知")
                || title.contains("意见")
                || title.contains("方案")
                || title.contains("公告")
                || title.contains("办法")
                || title.contains("决定")
                || title.contains("规划")
                || title.contains("行动")
                || title.contains("措施")
                || title.contains("指南");

        boolean urlLooksLikePolicy = url.contains("/zcwj/")
                || url.contains("/wjfb/")
                || url.contains("/jgsj/")
                || url.contains("/xxgk/")
                || url.contains("/art/");

        boolean themeMatched = text.contains("人工智能")
                || text.contains("制造")
                || text.contains("智能制造")
                || text.contains("大模型")
                || text.contains("智能体")
                || text.contains("工业互联网")
                || text.contains("数字化")
                || text.contains("数据集")
                || text.contains("算力");

        return titleLooksLikePolicy && urlLooksLikePolicy && themeMatched;
    }

    private String inferCategory(String sourceName, String sourceUrl, String title) {
        String source = safe(sourceName);
        String url = safe(sourceUrl);
        String text = safe(title);

        if (source.contains("山东")
                || url.contains("shandong.gov.cn")
                || url.contains("gxt.shandong.gov.cn")
                || text.contains("山东")) {
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

    private String firstText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);

            if (value != null && !value.isNull()) {
                return value.asText();
            }
        }

        return "";
    }

    private String cleanText(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("\u00A0", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String cleanUrl(String url) {
        if (url == null) {
            return "";
        }

        int index = url.indexOf("#");
        if (index >= 0) {
            url = url.substring(0, index);
        }

        return url.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
