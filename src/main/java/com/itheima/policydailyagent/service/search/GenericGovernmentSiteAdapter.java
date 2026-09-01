package com.itheima.policydailyagent.service.search;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

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
            int maxLinks
    ) {
        if (!supports(sourceUrl)) {
            throw new IllegalArgumentException("仅允许采集政府网站栏目页：" + sourceUrl);
        }
        try {
            Document document = Jsoup.connect(sourceUrl)
                    .userAgent(USER_AGENT)
                    .referrer("https://www.gov.cn/")
                    .timeout(15000)
                    .followRedirects(true)
                    .maxBodySize(0)
                    .get();
            return discoverFromDocument(sourceUrl, document, keywords, maxLinks);
        } catch (Exception e) {
            throw new RuntimeException("固定信源栏目页解析失败：" + e.getMessage(), e);
        }
    }

    public List<DiscoveredPolicyLink> discoverFromHtml(
            String sourceUrl,
            String html,
            List<String> keywords,
            int maxLinks
    ) {
        Document document = Jsoup.parse(html == null ? "" : html, sourceUrl);
        return discoverFromDocument(sourceUrl, document, keywords, maxLinks);
    }

    private List<DiscoveredPolicyLink> discoverFromDocument(
            String sourceUrl,
            Document document,
            List<String> keywords,
            int maxLinks
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
            if (!matchesKeywords(title + " " + url, keywords)) {
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
