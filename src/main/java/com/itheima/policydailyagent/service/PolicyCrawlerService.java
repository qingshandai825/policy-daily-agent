package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.dto.PolicyCrawlResult;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PolicyCrawlerService {

    private static final int MAX_CONTENT_LENGTH = 12000;

    private final PolicyAttachmentCrawlerService attachmentCrawlerService;

    public PolicyCrawlerService(PolicyAttachmentCrawlerService attachmentCrawlerService) {
        this.attachmentCrawlerService = attachmentCrawlerService;
    }

    public PolicyCrawlResult crawl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("政策网页链接不能为空");
        }

        try {
            Document document = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36")
                    .referrer("https://www.baidu.com/")
                    .timeout(15000)
                    .followRedirects(true)
                    .maxBodySize(0)
                    .get();

            String title = extractTitle(document);
            String sourceName = extractSourceName(url, document);
            LocalDate publishDate = extractPublishDate(document);
            String content = extractMainContent(document);

            if (content == null || content.isBlank()) {
                throw new RuntimeException("No valid policy content was extracted.");
            }

            PolicyAttachmentCrawlerService.AttachmentEnrichment enrichment =
                    attachmentCrawlerService.enrich(document, url, content);
            String cleanedContent = enrichment.cleanedContent();

            String sourceDomain = extractSourceDomain(url);

            return new PolicyCrawlResult(
                    title,
                    sourceName,
                    publishDate,
                    url,
                    content,
                    LocalDateTime.now(),
                    sourceDomain,
                    inferSourceType(sourceDomain),
                    inferAuthorityLevel(sourceDomain),
                    publishDate == null ? "UNDETECTED" : "PAGE_METADATA_OR_TEXT",
                    publishDate == null ? "" : publishDate.toString(),
                    publishDate == null ? "NONE" : "MEDIUM",
                    sha256(cleanedContent),
                    buildEvidenceSnippet(cleanedContent),
                    cleanedContent,
                    enrichment.contentCompleteness(),
                    enrichment.contentQualityReason(),
                    enrichment.attachments()
            );

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("网页抓取失败：" + e.getMessage(), e);
        }
    }

    private String extractTitle(Document document) {
        String title = "";

        Element h1 = document.selectFirst("h1");
        if (h1 != null) {
            title = h1.text();
        }

        if (isBlank(title)) {
            Element titleElement = document.selectFirst(
                    ".title, .article-title, .article_title, .main-title, .content-title, .detail-title"
            );
            if (titleElement != null) {
                title = titleElement.text();
            }
        }

        if (isBlank(title)) {
            title = firstNonBlank(
                    document.select("meta[property=og:title]").attr("content"),
                    document.select("meta[name=ArticleTitle]").attr("content"),
                    document.select("meta[name=title]").attr("content"),
                    document.select("meta[name=Title]").attr("content")
            );
        }

        if (isBlank(title)) {
            title = document.title();
        }

        return cleanTitle(title);
    }

    private String extractSourceName(String url, Document document) {
        String sourceName = firstNonBlank(
                document.select("meta[name=SiteName]").attr("content"),
                document.select("meta[name=sitename]").attr("content"),
                document.select("meta[name=source]").attr("content"),
                document.select("meta[name=Source]").attr("content"),
                document.select("meta[name=author]").attr("content")
        );

        if (isBlank(sourceName)) {
            String text = document.text();
            sourceName = extractSourceFromText(text);
        }

        if (isBlank(sourceName)) {
            sourceName = extractSourceFromHost(url);
        }

        return cleanText(sourceName);
    }

    private String extractSourceFromText(String text) {
        if (isBlank(text)) {
            return "";
        }

        Pattern pattern = Pattern.compile("(来源|发布机构|发文机关)[:：]\\s*([^\\s\\n]{2,30})");
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            return matcher.group(2);
        }

        return "";
    }

    private String extractSourceFromHost(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();

            if (host == null) {
                return "未知来源";
            }

            if (host.contains("miit.gov.cn")) {
                return "工业和信息化部";
            }
            if (host.contains("ndrc.gov.cn")) {
                return "国家发展改革委";
            }
            if (host.contains("nda.gov.cn")) {
                return "国家数据局";
            }
            if (host.contains("gxt.shandong.gov.cn")) {
                return "山东省工业和信息化厅";
            }
            if (host.contains("shandong.gov.cn")) {
                return "山东省人民政府";
            }

            return host;

        } catch (Exception e) {
            return "未知来源";
        }
    }

    private LocalDate extractPublishDate(Document document) {
        List<String> candidates = new ArrayList<>();

        candidates.add(document.select("meta[name=PubDate]").attr("content"));
        candidates.add(document.select("meta[name=pubdate]").attr("content"));
        candidates.add(document.select("meta[name=publishdate]").attr("content"));
        candidates.add(document.select("meta[name=PublishDate]").attr("content"));
        candidates.add(document.select("meta[name=ArticleDate]").attr("content"));
        candidates.add(document.select("meta[name=ContentSourceTime]").attr("content"));
        candidates.add(document.text());

        Pattern pattern = Pattern.compile("(20\\d{2})[-年./](\\d{1,2})[-月./](\\d{1,2})");

        for (String candidate : candidates) {
            if (isBlank(candidate)) {
                continue;
            }

            Matcher matcher = pattern.matcher(candidate);

            if (matcher.find()) {
                try {
                    int year = Integer.parseInt(matcher.group(1));
                    int month = Integer.parseInt(matcher.group(2));
                    int day = Integer.parseInt(matcher.group(3));

                    return LocalDate.of(year, month, day);
                } catch (Exception ignored) {
                }
            }
        }

        return null;
    }

    private String extractMainContent(Document document) {
        Document doc = document.clone();

        // 删除明显不属于正文的标签
        doc.select("script, style, nav, header, footer, iframe, noscript, form, button").remove();

        // 删除常见导航、面包屑、分享、分页、打印、侧边栏等区域
        doc.select(
                ".breadcrumb, .crumb, .location, .position, .nav, .navbar, " +
                        ".header, .footer, .share, .print, .page, .pages, .pagination, " +
                        ".sidebar, .side, .menu, .toolbar, .tools, .search, .login"
        ).remove();

        String[] selectors = {
                "#UCAP-CONTENT",
                "#ucap_content",
                "#zoom",
                "#content",
                ".TRS_Editor",
                ".trs_editor",
                "div[class*=TRS_Editor]",
                ".article-content",
                ".article_content",
                ".article-con",
                ".articleCon",
                ".article",
                ".detail-content",
                ".detail_content",
                ".detailContent",
                ".main-content",
                ".main_content",
                ".news-content",
                ".news_content",
                ".pages_content",
                ".text-content",
                ".text_content",
                ".con_txt",
                ".content"
        };

        String bestText = "";
        int bestScore = -1;

        for (String selector : selectors) {
            Elements elements = doc.select(selector);

            for (Element element : elements) {
                String text = cleanContentText(extractTextWithLines(element));
                int score = contentScore(text);

                if (score > bestScore) {
                    bestText = text;
                    bestScore = score;
                }
            }
        }

        // 如果常规正文选择器没命中，则从 h1 附近区域尝试提取
        if (isBlank(bestText)) {
            Element h1 = doc.selectFirst("h1");

            if (h1 != null) {
                Element current = h1.parent();

                for (int i = 0; i < 3 && current != null; i++) {
                    String text = cleanContentText(extractTextWithLines(current));
                    int score = contentScore(text);

                    if (score > bestScore) {
                        bestText = text;
                        bestScore = score;
                    }

                    current = current.parent();
                }
            }
        }

        // 最后才使用 body 兜底，并进行严格清洗
        if (isBlank(bestText)) {
            Element body = doc.body();

            if (body != null) {
                bestText = cleanContentText(extractTextWithLines(body));
            }
        }

        if (bestText.length() > MAX_CONTENT_LENGTH) {
            bestText = bestText.substring(0, MAX_CONTENT_LENGTH);
        }

        return bestText;
    }

    private String extractTextWithLines(Element element) {
        StringBuilder builder = new StringBuilder();

        Elements blocks = element.select("h1, h2, h3, h4, p, li, td, tr");

        for (Element block : blocks) {
            String line = block.text();

            if (!isBlank(line)) {
                builder.append(line).append("\n");
            }
        }

        if (builder.length() < 100) {
            return element.wholeText();
        }

        return builder.toString();
    }

    private String cleanContentText(String rawText) {
        if (isBlank(rawText)) {
            return "";
        }

        String normalized = rawText
                .replace("\u00A0", " ")
                .replace("　", " ")
                .replace("\r", "\n");

        String[] lines = normalized.split("\n");

        List<String> cleanedLines = new ArrayList<>();
        String previousLine = "";

        for (String line : lines) {
            String cleaned = line
                    .replaceAll("\\s+", " ")
                    .trim();

            if (cleaned.isBlank()) {
                continue;
            }

            if (isNoiseLine(cleaned)) {
                continue;
            }

            if (cleaned.equals(previousLine)) {
                continue;
            }

            cleanedLines.add(cleaned);
            previousLine = cleaned;
        }

        return String.join("\n", cleanedLines).trim();
    }

    private boolean isNoiseLine(String line) {
        if (isBlank(line)) {
            return true;
        }

        String compact = line.replace(" ", "");

        // 过短的纯功能词
        if (compact.matches("^(首页|下一页|上一页|尾页|末页|返回|关闭|更多|搜索)$")) {
            return true;
        }

        // 面包屑导航
        if ((compact.startsWith("首页") || compact.startsWith("当前位置")) && compact.length() < 100) {
            return true;
        }

        if (compact.contains("首页>")
                || compact.contains("首页＞")
                || compact.contains("当前位置:")
                || compact.contains("当前位置：")) {
            return true;
        }

        // 页面操作按钮
        if (compact.contains("返回顶部")
                || compact.contains("关闭窗口")
                || compact.contains("打印本页")
                || compact.equals("打印")
                || compact.equals("分享")) {
            return true;
        }

        // 分页
        if (compact.matches("^(上一页|下一页|第一页|最后一页|共\\d+页.*|第\\d+页.*)$")) {
            return true;
        }

        // 字体、字号、移动端提示等
        if (compact.contains("字号")
                || compact.contains("字体")
                || compact.contains("扫一扫")
                || compact.contains("手机打开当前页")
                || compact.contains("无障碍")
                || compact.contains("网站地图")
                || compact.contains("RSS订阅")) {
            return true;
        }

        // 页脚信息
        if (compact.contains("ICP备案")
                || compact.contains("网站标识码")
                || compact.contains("主办单位")
                || compact.contains("版权所有")
                || compact.contains("技术支持")) {
            return true;
        }

        // 元信息字段，这些已经单独提取，不作为正文
        if (compact.matches("^(发文机关|标题|发文字号|成文日期|发布日期|发布机构|分类|来源)[:：].*")) {
            return true;
        }

        // 发布时间来源行
        if (compact.matches("^发布时间[:：].*来源[:：].*")) {
            return true;
        }

        return false;
    }

    private int contentScore(String text) {
        if (!isValidContentCandidate(text)) {
            return -1;
        }

        int lengthScore = Math.min(text.length(), 5000);

        int lineCount = text.split("\n").length;
        int lineScore = Math.min(lineCount * 20, 1000);

        int noisePenalty = 0;

        if (text.contains("首页")) {
            noisePenalty += 500;
        }
        if (text.contains("返回顶部")) {
            noisePenalty += 1000;
        }
        if (text.contains("关闭窗口")) {
            noisePenalty += 1000;
        }
        if (text.contains("打印本页")) {
            noisePenalty += 1000;
        }
        if (text.contains("网站地图")) {
            noisePenalty += 1000;
        }

        return lengthScore + lineScore - noisePenalty;
    }

    private boolean isValidContentCandidate(String text) {
        if (isBlank(text) || text.length() < 100) {
            return false;
        }

        long chineseCount = text.chars()
                .filter(ch -> ch >= 0x4E00 && ch <= 0x9FA5)
                .count();

        if (chineseCount < 50) {
            return false;
        }

        int noiseCount = 0;

        if (text.contains("返回顶部")) {
            noiseCount++;
        }
        if (text.contains("关闭窗口")) {
            noiseCount++;
        }
        if (text.contains("打印本页")) {
            noiseCount++;
        }
        if (text.contains("网站地图")) {
            noiseCount++;
        }

        return noiseCount < 3;
    }

    private String cleanTitle(String title) {
        String cleaned = cleanText(title);

        cleaned = cleaned.replaceAll("_中华人民共和国工业和信息化部.*$", "");
        cleaned = cleaned.replaceAll("-中华人民共和国工业和信息化部.*$", "");
        cleaned = cleaned.replaceAll("_工业和信息化部.*$", "");
        cleaned = cleaned.replaceAll("-工业和信息化部.*$", "");
        cleaned = cleaned.replaceAll("_山东省工业和信息化厅.*$", "");
        cleaned = cleaned.replaceAll("-山东省工业和信息化厅.*$", "");

        return cleaned.trim();
    }

    private String extractSourceDomain(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return host == null ? "" : host.toLowerCase();
        } catch (Exception e) {
            return "";
        }
    }

    private String inferSourceType(String sourceDomain) {
        String domain = safeLower(sourceDomain);

        if (domain.endsWith(".gov.cn") || domain.contains("gov.cn")) {
            return "GOVERNMENT";
        }

        if (domain.endsWith(".edu.cn")) {
            return "RESEARCH_OR_EDUCATION";
        }

        return "OTHER";
    }

    private String inferAuthorityLevel(String sourceDomain) {
        String domain = safeLower(sourceDomain);

        if (domain.contains("gov.cn")
                && (domain.contains("www.gov.cn")
                || domain.contains("miit.gov.cn")
                || domain.contains("ndrc.gov.cn")
                || domain.contains("nda.gov.cn")
                || domain.contains("cac.gov.cn"))) {
            return "NATIONAL_AUTHORITY";
        }

        if (domain.contains("gov.cn")) {
            return "LOCAL_OR_DEPARTMENT_AUTHORITY";
        }

        return "UNVERIFIED";
    }

    private String sha256(String text) {
        if (text == null) {
            return "";
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "";
        }
    }

    private String buildEvidenceSnippet(String content) {
        String cleaned = cleanContentText(content);

        if (cleaned.length() <= 1000) {
            return cleaned;
        }

        return cleaned.substring(0, 1000);
    }

    private String safeLower(String value) {
        return value == null ? "" : value.trim().toLowerCase();
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

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }

        return "";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
