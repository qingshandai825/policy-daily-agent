package com.itheima.policydailyagent.service.search;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itheima.policydailyagent.service.PolicyHttpFetcher;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class GenericGovernmentSiteAdapter implements PolicySiteAdapter {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public boolean supports(String sourceUrl) {
        if (!hasText(sourceUrl)) {
            return false;
        }
        try {
            String host = URI.create(sourceUrl).getHost();
            return host != null && host.toLowerCase(Locale.ROOT).contains("gov.cn");
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public List<DiscoveredPolicyLink> discover(
            String sourceUrl,
            List<String> keywords,
            int maxLinks,
            boolean filterByKeyword
    ) {
        if (!supports(sourceUrl)) {
            throw new IllegalArgumentException("仅允许采集政府网站栏目页：" + sourceUrl);
        }
        try {
            // 政府栏目页多为前端渲染，静态 HTML 不含文章链接。
            // 对已知的列表页（如“最新政策”）改用官方 JSON 数据接口。
            String govJson = resolveGovListingJson(sourceUrl);
            if (govJson != null) {
                String body = PolicyHttpFetcher.connect(govJson)
                        .ignoreContentType(true)
                        .maxBodySize(0)
                        .execute()
                        .body();
                return discoverFromGovJson(body, keywords, maxLinks, filterByKeyword);
            }
            Document document = PolicyHttpFetcher.connect(sourceUrl)
                    .referrer("https://www.gov.cn/")
                    .get();
            return discoverFromDocument(sourceUrl, document, keywords, maxLinks, filterByKeyword);
        } catch (Exception e) {
            throw new RuntimeException("固定信源栏目页解析失败：" + e.getMessage(), e);
        }
    }

    /**
     * 已知中国政府网栏目页配套的数据接口。
     * 例：https://www.gov.cn/zhengce/zuixin/  ->  .../ZUIXINZHENGCE.json
     */
    private String resolveGovListingJson(String sourceUrl) {
        String lower = sourceUrl.toLowerCase(Locale.ROOT);
        if (lower.contains("/zhengce/zuixin")) {
            return withTrailingSlash(canonicalize(sourceUrl)) + "ZUIXINZHENGCE.json";
        }
        return null;
    }

    private String withTrailingSlash(String value) {
        return value.endsWith("/") ? value : value + "/";
    }

    private List<DiscoveredPolicyLink> discoverFromGovJson(
            String json,
            List<String> keywords,
            int maxLinks,
            boolean filterByKeyword
    ) throws IOException {
        int limit = Math.max(1, maxLinks);
        JsonNode root = MAPPER.readTree(json);
        if (!root.isArray()) {
            return List.of();
        }
        Map<String, DiscoveredPolicyLink> discovered = new LinkedHashMap<>();
        for (JsonNode node : root) {
            String title = text(node, "TITLE");
            String url = canonicalize(text(node, "URL"));
            String sub = text(node, "SUB_TITLE");
            String date = text(node, "DOCRELPUBTIME");
            if (!hasText(url) || !isLikelyArticleUrl(url)) {
                continue;
            }
            String snippet = ((hasText(sub) ? sub : "")
                    + (hasText(date) ? "（发布日期：" + date + "）" : "")).trim();
            String matchText = title + " " + url + " " + sub;
            if (filterByKeyword && !matchesKeywords(matchText, keywords)) {
                continue;
            }
            discovered.putIfAbsent(url, new DiscoveredPolicyLink(title, url, snippet));
            if (discovered.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(discovered.values());
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null ? "" : value.asText("");
    }

    public List<DiscoveredPolicyLink> discoverFromHtml(
            String sourceUrl,
            String html,
            List<String> keywords,
            int maxLinks,
            boolean filterByKeyword
    ) {
        Document document = Jsoup.parse(html == null ? "" : html, sourceUrl);
        return discoverFromDocument(sourceUrl, document, keywords, maxLinks, filterByKeyword);
    }

    private List<DiscoveredPolicyLink> discoverFromDocument(
            String sourceUrl,
            Document document,
            List<String> keywords,
            int maxLinks,
            boolean filterByKeyword
    ) {
        int limit = Math.max(1, maxLinks);
        Map<String, DiscoveredPolicyLink> discovered = new LinkedHashMap<>();
        String canonicalSource = canonicalize(sourceUrl);

        for (Element link : document.select("a[href]")) {
            String title = cleanText(link.text());
            String url = canonicalize(link.absUrl("href"));
            if (!hasText(url)) {
                url = canonicalize(resolve(sourceUrl, link.attr("href")));
            }
            if (!hasText(url) || url.equals(canonicalSource) || !isLikelyArticleUrl(url)) {
                continue;
            }
            // 关键词匹配范围：标题 + URL + 链接所在区块的周边文本。
            // 政府栏目页的标题往往不直书“人工智能”，而列表项的父级摘要里常含主题词。
            if (filterByKeyword && !matchesKeywords(title + " " + url + " " + parentText(link), keywords)) {
                continue;
            }

            String snippet = cleanText(link.attr("title"));
            if (!hasText(snippet) && link.parent() != null) {
                snippet = cleanText(link.parent().text());
            }
            if (snippet.length() > 300) {
                snippet = snippet.substring(0, 300);
            }
            discovered.putIfAbsent(url, new DiscoveredPolicyLink(title, url, snippet));
            if (discovered.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(discovered.values());
    }

    private String parentText(Element link) {
        Element parent = link.parent();
        if (parent == null) {
            return "";
        }
        String text = cleanText(parent.text());
        return text.length() > 200 ? text.substring(0, 200) : text;
    }

    private boolean matchesKeywords(String text, List<String> keywords) {
        if (keywords == null || keywords.stream().noneMatch(this::hasText)) {
            return true;
        }
        String candidate = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return keywords.stream()
                .filter(this::hasText)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(candidate::contains);
    }

    private boolean isLikelyArticleUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return false;
        }
        if (lower.endsWith("/index.html") || lower.endsWith("/index.htm")) {
            return false;
        }
        if (lower.matches(".*\\.(pdf|docx?|xlsx?|zip|rar|jpg|jpeg|png)(\\?.*)?$")) {
            return false;
        }
        return lower.endsWith(".html")
                || lower.endsWith(".htm")
                || lower.contains("/art/")
                || lower.contains("/content_")
                || lower.contains("/detail/")
                || lower.contains("/info/")
                || lower.matches(".*/20\\d{2}/.*");
    }

    String canonicalize(String value) {
        if (!hasText(value)) {
            return "";
        }
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
                port = -1;
            }
            String path = uri.getPath();
            if (path != null && path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            return new URI(scheme, uri.getUserInfo(), host, port, path, uri.getQuery(), null).toString();
        } catch (Exception ignored) {
            int fragment = value.indexOf('#');
            return (fragment >= 0 ? value.substring(0, fragment) : value).trim();
        }
    }

    private String resolve(String baseUrl, String href) {
        try {
            return URI.create(baseUrl).resolve(href).toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String cleanText(String value) {
        return value == null ? "" : value
                .replace("\u00A0", " ")
                .replace("　", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
