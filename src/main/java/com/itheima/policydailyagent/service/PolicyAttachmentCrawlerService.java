package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.domain.policy.AttachmentExtractionStatus;
import com.itheima.policydailyagent.domain.policy.ContentCompleteness;
import com.itheima.policydailyagent.dto.PolicyAttachmentCrawlResult;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class PolicyAttachmentCrawlerService {

    private static final int MAX_ATTACHMENT_BYTES = 25 * 1024 * 1024;
    private static final int MAX_EXTRACTED_CHARACTERS = 250_000;
    private static final int MIN_EXTRACTED_CHARACTERS = 80;
    public AttachmentEnrichment enrich(Document document, String pageUrl, String pageContent) {
        List<AttachmentLink> links = discover(document, pageUrl);
        List<PolicyAttachmentCrawlResult> attachments = links.stream()
                .map(this::downloadAndExtract)
                .toList();
        return assess(pageContent, attachments);
    }

    AttachmentEnrichment assess(
            String pageContent,
            List<PolicyAttachmentCrawlResult> attachments
    ) {
        List<PolicyAttachmentCrawlResult> safeAttachments = attachments == null
                ? List.of()
                : attachments;
        Map<String, PolicyAttachmentCrawlResult> successfulGroups = new LinkedHashMap<>();
        Set<String> allGroups = new LinkedHashSet<>();
        for (PolicyAttachmentCrawlResult attachment : safeAttachments) {
            String group = logicalGroup(attachment.fileName(), attachment.sourceUrl());
            allGroups.add(group);
            if (attachment.extractionStatus() == AttachmentExtractionStatus.SUCCEEDED) {
                successfulGroups.putIfAbsent(group, attachment);
            }
        }

        ContentCompleteness completeness;
        String reason;
        if (isBlank(pageContent)) {
            completeness = successfulGroups.isEmpty()
                    ? ContentCompleteness.FAILED
                    : ContentCompleteness.PARTIAL;
            reason = successfulGroups.isEmpty()
                    ? "页面正文与附件正文均不可用"
                    : "页面正文为空，仅提取到部分附件正文";
        } else if (safeAttachments.isEmpty()) {
            completeness = ContentCompleteness.COMPLETE;
            reason = "页面未发现政策正文附件，HTML 正文可直接分析";
        } else if (successfulGroups.size() == allGroups.size()) {
            completeness = ContentCompleteness.COMPLETE;
            reason = "发现 " + safeAttachments.size() + " 个附件（" + allGroups.size()
                    + " 组），每组至少有一种格式成功解析";
        } else if (!successfulGroups.isEmpty()) {
            completeness = ContentCompleteness.PARTIAL;
            reason = "仅覆盖 " + successfulGroups.size() + "/" + allGroups.size()
                    + " 组附件，需补齐失败或不支持的附件";
        } else {
            completeness = ContentCompleteness.PAGE_ONLY;
            reason = "发现 " + safeAttachments.size() + " 个附件，但均未提取到可用正文";
        }

        String combined = combine(pageContent, successfulGroups.values());
        return new AttachmentEnrichment(
                combined,
                completeness,
                reason,
                List.copyOf(safeAttachments)
        );
    }

    List<AttachmentLink> discover(Document document, String pageUrl) {
        if (document == null) {
            return List.of();
        }
        Map<String, AttachmentLink> unique = new LinkedHashMap<>();
        for (Element link : document.select("a[href]")) {
            String href = absoluteUrl(link, pageUrl);
            String label = cleanLabel(link.text());
            if (isBlank(href)) {
                continue;
            }
            String type = detectFileType(href, label);
            if (type == null) {
                continue;
            }
            String fileName = isBlank(label) || isGenericDownloadLabel(label)
                    ? fileNameFromUrl(href)
                    : label;
            unique.putIfAbsent(href, new AttachmentLink(limit(fileName, 500), href, type));
        }
        return List.copyOf(unique.values());
    }

    PolicyAttachmentCrawlResult downloadAndExtract(AttachmentLink link) {
        if (!"PDF".equals(link.fileType()) && !"DOCX".equals(link.fileType())) {
            return result(
                    link, null, AttachmentExtractionStatus.UNSUPPORTED,
                    null, "当前版本暂不解析 " + link.fileType() + "，已保留附件链接"
            );
        }

        try {
            Connection.Response response = Jsoup.connect(link.sourceUrl())
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36")
                    .referrer(link.sourceUrl())
                    .timeout(30_000)
                    .followRedirects(true)
                    .ignoreContentType(true)
                    .maxBodySize(MAX_ATTACHMENT_BYTES + 1)
                    .execute();
            byte[] bytes = response.bodyAsBytes();
            if (bytes.length > MAX_ATTACHMENT_BYTES) {
                return result(
                        link, response.contentType(), AttachmentExtractionStatus.FAILED,
                        null, "附件超过 25MB 安全上限"
                );
            }

            String extracted = parseAttachmentBytes(bytes, link.fileType());
            if (extracted.length() < MIN_EXTRACTED_CHARACTERS) {
                return result(
                        link, response.contentType(), AttachmentExtractionStatus.FAILED,
                        null, "未提取到足够的文本，附件可能是扫描件或受保护文件"
                );
            }
            return result(
                    link, response.contentType(), AttachmentExtractionStatus.SUCCEEDED,
                    extracted, null
            );
        } catch (Exception e) {
            return result(
                    link, null, AttachmentExtractionStatus.FAILED,
                    null, "附件下载或解析失败：" + limit(e.getMessage(), 1500)
            );
        }
    }

    String parseAttachmentBytes(byte[] bytes, String fileType) throws Exception {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        String raw;
        if ("PDF".equals(fileType)) {
            try (PDDocument document = Loader.loadPDF(bytes)) {
                if (!document.getCurrentAccessPermission().canExtractContent()) {
                    throw new IllegalArgumentException("PDF 禁止提取文本");
                }
                raw = new PDFTextStripper().getText(document);
            }
        } else if ("DOCX".equals(fileType)) {
            try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
                 XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                raw = extractor.getText();
            }
        } else {
            throw new IllegalArgumentException("不支持的附件类型：" + fileType);
        }
        return normalizeExtractedText(raw);
    }

    private PolicyAttachmentCrawlResult result(
            AttachmentLink link,
            String contentType,
            AttachmentExtractionStatus status,
            String extractedContent,
            String errorMessage
    ) {
        String text = extractedContent == null ? null : limit(extractedContent, MAX_EXTRACTED_CHARACTERS);
        return new PolicyAttachmentCrawlResult(
                link.fileName(),
                link.sourceUrl(),
                link.fileType(),
                contentType,
                status,
                text,
                text == null ? null : sha256(text),
                text == null ? 0 : text.length(),
                errorMessage
        );
    }

    private String combine(
            String pageContent,
            Collection<PolicyAttachmentCrawlResult> successfulAttachments
    ) {
        StringBuilder combined = new StringBuilder(safe(pageContent));
        for (PolicyAttachmentCrawlResult attachment : successfulAttachments) {
            if (isBlank(attachment.extractedContent())) {
                continue;
            }
            if (!combined.isEmpty()) {
                combined.append("\n\n");
            }
            combined.append("【附件：")
                    .append(attachment.fileName())
                    .append("】\n")
                    .append(attachment.extractedContent());
        }
        return limit(combined.toString().trim(), MAX_EXTRACTED_CHARACTERS);
    }

    private String normalizeExtractedText(String raw) {
        if (isBlank(raw)) {
            return "";
        }
        List<String> result = new ArrayList<>();
        String previous = "";
        for (String line : raw.replace("\r", "\n").split("\n")) {
            String cleaned = line.replace("\u00A0", " ")
                    .replace("　", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
            if (cleaned.isBlank() || cleaned.equals(previous)) {
                continue;
            }
            result.add(cleaned);
            previous = cleaned;
        }
        return limit(String.join("\n", result), MAX_EXTRACTED_CHARACTERS);
    }

    private String absoluteUrl(Element link, String pageUrl) {
        String absolute = link.absUrl("href");
        if (!isBlank(absolute)) {
            return absolute;
        }
        try {
            URI resolved = URI.create(pageUrl).resolve(link.attr("href"));
            String scheme = safe(resolved.getScheme()).toLowerCase(Locale.ROOT);
            return ("http".equals(scheme) || "https".equals(scheme))
                    ? resolved.toString()
                    : "";
        } catch (Exception e) {
            return "";
        }
    }

    private String detectFileType(String url, String label) {
        String value = (safe(url) + " " + safe(label)).toLowerCase(Locale.ROOT);
        if (value.matches(".*\\.pdf(?:[?#].*)?(?:\\s.*)?$") || value.contains("pdf文件")) {
            return "PDF";
        }
        if (value.matches(".*\\.ofd(?:[?#].*)?(?:\\s.*)?$") || value.contains("ofd文件")) {
            return "OFD";
        }
        if (value.matches(".*\\.docx(?:[?#].*)?(?:\\s.*)?$")) {
            return "DOCX";
        }
        if (value.matches(".*\\.doc(?:[?#].*)?(?:\\s.*)?$") || value.contains("word文件")) {
            return "DOC";
        }
        return null;
    }

    private String logicalGroup(String fileName, String sourceUrl) {
        String value = isBlank(fileName) ? fileNameFromUrl(sourceUrl) : fileName;
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("(?i)\\.(pdf|ofd|docx?|wps)$", "")
                .replaceAll("[《》<>（）()\\[\\]【】\\s]+", "");
    }

    private String fileNameFromUrl(String url) {
        try {
            String path = URI.create(url).getPath();
            int slash = path.lastIndexOf('/');
            String fileName = slash >= 0 ? path.substring(slash + 1) : path;
            return cleanLabel(URLDecoder.decode(fileName, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return "政策附件";
        }
    }

    private boolean isGenericDownloadLabel(String label) {
        String compact = safe(label).replaceAll("\\s+", "");
        return compact.matches("(?i)^(下载|点击下载|附件|附件下载|download)$");
    }

    private String cleanLabel(String value) {
        return safe(value).replaceAll("\\s+", " ").trim();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return "";
        }
    }

    private String limit(String value, int maxLength) {
        String safe = safe(value);
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public record AttachmentEnrichment(
            String cleanedContent,
            ContentCompleteness contentCompleteness,
            String contentQualityReason,
            List<PolicyAttachmentCrawlResult> attachments
    ) {
        public int extractedAttachmentCount() {
            return (int) attachments.stream()
                    .filter(item -> item.extractionStatus() == AttachmentExtractionStatus.SUCCEEDED)
                    .count();
        }
    }

    record AttachmentLink(String fileName, String sourceUrl, String fileType) {
    }
}
