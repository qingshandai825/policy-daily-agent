package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.dto.MonthlyReportGenerateRequest;
import com.itheima.policydailyagent.entity.PolicyDocument;
import com.itheima.policydailyagent.repository.PolicyDocumentRepository;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MonthlyReportService {

    private static final String FONT_FANGSONG_GB2312 = "仿宋_GB2312";
    private static final int FONT_SIZE_THIRD = 16; // 三号 = 16pt

    private final PolicyDocumentRepository policyDocumentRepository;
    private final MonthlyReportAiService monthlyReportAiService;

    public MonthlyReportService(
            PolicyDocumentRepository policyDocumentRepository,
            MonthlyReportAiService monthlyReportAiService
    ) {
        this.policyDocumentRepository = policyDocumentRepository;
        this.monthlyReportAiService = monthlyReportAiService;
    }

    public byte[] generateMonthlyReport(MonthlyReportGenerateRequest request) {
        MonthlyReportGenerateRequest safeRequest = request == null
                ? new MonthlyReportGenerateRequest(null, null, null, null, null, null, null, null)
                : request;

        List<PolicyDocument> documents =
                policyDocumentRepository.findTop20ByStatusOrderByPublishDateDescCreatedAtDesc("SUMMARIZED");

        List<PolicyDocument> nationalDocuments = documents.stream()
                .filter(this::isNationalPolicy)
                .toList();

        List<PolicyDocument> provincialDocuments = documents.stream()
                .filter(this::isProvincialPolicy)
                .toList();

        Map<String, String> placeholders = buildPlaceholderMap(
                safeRequest,
                nationalDocuments,
                provincialDocuments
        );

        return generateReportFromPlaceholders(placeholders);
    }

    private byte[] generateReportFromPlaceholders(Map<String, String> placeholders) {
        try (InputStream inputStream = new ClassPathResource("templates/monthly_report_template.docx").getInputStream();
             XWPFDocument document = new XWPFDocument(inputStream);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            replaceSectionPlaceholdersWithFormattedRuns(document, placeholders);
            replaceKeyPointPlaceholdersWithBullets(document, placeholders);
            replaceNormalPlaceholders(document, placeholders);

            document.write(outputStream);
            return outputStream.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException(
                    "Word report generation failed: " + e.getClass().getSimpleName() + " - " + e.getMessage(),
                    e
            );
        }
    }

    private Map<String, String> buildPlaceholderMap(
            MonthlyReportGenerateRequest request,
            List<PolicyDocument> nationalDocuments,
            List<PolicyDocument> provincialDocuments
    ) {
        String reportMonth = hasText(request.reportMonth())
                ? request.reportMonth()
                : YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy年M月"));

        Map<String, String> map = new HashMap<>();

        map.put("{{REPORT_MONTH}}", reportMonth);

        map.put(
                "{{KEY_POINTS_NATIONAL}}",
                monthlyReportAiService.generateKeyPoints(
                        "国家重点事项",
                        nationalDocuments,
                        "本期暂无国家重点事项自动检索结果。"
                )
        );

        map.put(
                "{{KEY_POINTS_PROVINCIAL}}",
                monthlyReportAiService.generateKeyPoints(
                        "省内工作推进",
                        provincialDocuments,
                        "本期暂无省内工作推进自动检索结果。"
                )
        );

        map.put(
                "{{KEY_POINTS_PIONEER}}",
                "本期先锋应用动态待结合地市调度数据和进度统计表补充。"
        );

        map.put(
                "{{KEY_POINTS_CASE}}",
                "本期典型应用案例待结合企业案例库和公开材料补充。"
        );

        map.put(
                "{{KEY_POINTS_TREND}}",
                "本期产业趋势洞察待结合政策动态、行业案例和公开资料进一步研判。"
        );

        map.put(
                "{{SECTION_NATIONAL}}",
                monthlyReportAiService.generateNationalSection(
                        nationalDocuments,
                        "本期暂无国家重点事项自动检索结果。"
                )
        );

        map.put(
                "{{SECTION_PROVINCIAL}}",
                monthlyReportAiService.generateProvincialSection(
                        provincialDocuments,
                        "本期暂无省内工作推进自动检索结果。"
                )
        );

        map.put(
                "{{SECTION_CASE}}",
                "本期典型应用案例待结合企业案例库和公开材料补充。"
        );

        map.put(
                "{{SECTION_TREND}}",
                "本期产业趋势洞察待结合政策动态、行业案例和公开资料进一步研判。"
        );

        map.put("{{REPORT_TO}}", hasText(request.reportTo()) ? request.reportTo() : "XXXXX");
        map.put("{{SEND_TO}}", hasText(request.sendTo()) ? request.sendTo() : "XXXXX");
        map.put("{{CONTACT_INFO}}", hasText(request.contactInfo()) ? request.contactInfo() : "XXX   XXXXXXXX");

        return map;
    }

    /**
     * 处理正文栏目：
     * {{SECTION_NATIONAL}}
     * {{SECTION_PROVINCIAL}}
     *
     * 标题：仿宋_GB2312、三号、加粗
     * 正文：仿宋_GB2312、三号、不加粗
     */
    private void replaceSectionPlaceholdersWithFormattedRuns(
            XWPFDocument document,
            Map<String, String> placeholders
    ) {
        replaceOneSectionPlaceholder(document, placeholders, "{{SECTION_NATIONAL}}");
        replaceOneSectionPlaceholder(document, placeholders, "{{SECTION_PROVINCIAL}}");

        placeholders.remove("{{SECTION_NATIONAL}}");
        placeholders.remove("{{SECTION_PROVINCIAL}}");
    }

    private void replaceOneSectionPlaceholder(
            XWPFDocument document,
            Map<String, String> placeholders,
            String placeholder
    ) {
        String sectionText = placeholders.get(placeholder);

        if (!hasText(sectionText)) {
            return;
        }

        for (XWPFParagraph paragraph : document.getParagraphs()) {
            String text = paragraph.getText();

            if (text != null && text.contains(placeholder)) {
                replaceParagraphWithFormattedSectionRuns(paragraph, sectionText);
                return;
            }
        }

        for (XWPFTable table : document.getTables()) {
            if (replaceSectionPlaceholderInTable(table, placeholder, sectionText)) {
                return;
            }
        }
    }

    private boolean replaceSectionPlaceholderInTable(
            XWPFTable table,
            String placeholder,
            String sectionText
    ) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph paragraph : cell.getParagraphs()) {
                    String text = paragraph.getText();

                    if (text != null && text.contains(placeholder)) {
                        replaceParagraphWithFormattedSectionRuns(paragraph, sectionText);
                        return true;
                    }
                }

                for (XWPFTable nestedTable : cell.getTables()) {
                    if (replaceSectionPlaceholderInTable(nestedTable, placeholder, sectionText)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private void replaceParagraphWithFormattedSectionRuns(
            XWPFParagraph paragraph,
            String sectionText
    ) {
        clearParagraph(paragraph);

        List<SectionItem> items = parseSectionItems(sectionText);

        if (items.isEmpty()) {
            XWPFRun run = createNormalRun(paragraph);
            replaceRunTextWithBreaks(run, sectionText);
            return;
        }

        // 不依赖 Word 的首行缩进，改为用全角空格控制每条标题和正文缩进
        paragraph.setFirstLineIndent(0);
        paragraph.setIndentationLeft(0);
        paragraph.setSpacingAfter(120);

        for (int i = 0; i < items.size(); i++) {
            SectionItem item = items.get(i);

            // 标题：两个全角空格 + 编号标题
            XWPFRun titleRun = createTitleRun(paragraph);
            titleRun.setText(item.title());
            titleRun.addBreak();

            // 正文：两个全角空格 + 正文
            XWPFRun bodyRun = createNormalRun(paragraph);
            String body = hasText(item.body()) ? item.body() : "材料未明确。";
            replaceRunTextWithBreaks(bodyRun, "　　" + body);

            if (i < items.size() - 1) {
                bodyRun.addBreak();
                bodyRun.addBreak();
            }
        }
    }

    /**
     * 处理“本期要目”项目符号：
     * {{KEY_POINTS_NATIONAL}}
     * {{KEY_POINTS_PROVINCIAL}}
     * {{KEY_POINTS_PIONEER}}
     * {{KEY_POINTS_CASE}}
     * {{KEY_POINTS_TREND}}
     *
     * 输出格式：
     * ● xxxx
     * ● xxxx
     */
    private void replaceKeyPointPlaceholdersWithBullets(
            XWPFDocument document,
            Map<String, String> placeholders
    ) {
        replaceOneKeyPointPlaceholder(document, placeholders, "{{KEY_POINTS_NATIONAL}}");
        replaceOneKeyPointPlaceholder(document, placeholders, "{{KEY_POINTS_PROVINCIAL}}");
        replaceOneKeyPointPlaceholder(document, placeholders, "{{KEY_POINTS_PIONEER}}");
        replaceOneKeyPointPlaceholder(document, placeholders, "{{KEY_POINTS_CASE}}");
        replaceOneKeyPointPlaceholder(document, placeholders, "{{KEY_POINTS_TREND}}");

        placeholders.remove("{{KEY_POINTS_NATIONAL}}");
        placeholders.remove("{{KEY_POINTS_PROVINCIAL}}");
        placeholders.remove("{{KEY_POINTS_PIONEER}}");
        placeholders.remove("{{KEY_POINTS_CASE}}");
        placeholders.remove("{{KEY_POINTS_TREND}}");
    }

    private void replaceOneKeyPointPlaceholder(
            XWPFDocument document,
            Map<String, String> placeholders,
            String placeholder
    ) {
        String keyPointText = placeholders.get(placeholder);

        if (!hasText(keyPointText)) {
            return;
        }

        for (XWPFParagraph paragraph : document.getParagraphs()) {
            String text = paragraph.getText();

            if (text != null && text.contains(placeholder)) {
                replaceParagraphWithBulletRuns(paragraph, keyPointText);
                return;
            }
        }

        for (XWPFTable table : document.getTables()) {
            if (replaceKeyPointPlaceholderInTable(table, placeholder, keyPointText)) {
                return;
            }
        }
    }

    private boolean replaceKeyPointPlaceholderInTable(
            XWPFTable table,
            String placeholder,
            String keyPointText
    ) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph paragraph : cell.getParagraphs()) {
                    String text = paragraph.getText();

                    if (text != null && text.contains(placeholder)) {
                        replaceParagraphWithBulletRuns(paragraph, keyPointText);
                        return true;
                    }
                }

                for (XWPFTable nestedTable : cell.getTables()) {
                    if (replaceKeyPointPlaceholderInTable(nestedTable, placeholder, keyPointText)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private void replaceParagraphWithBulletRuns(
            XWPFParagraph paragraph,
            String keyPointText
    ) {
        clearParagraph(paragraph);

        List<String> items = parseKeyPointItems(keyPointText);

        if (items.isEmpty()) {
            XWPFRun run = createNormalRun(paragraph);
            replaceRunTextWithBreaks(run, "● " + keyPointText.trim());
            return;
        }

        paragraph.setFirstLineIndent(0);
        paragraph.setSpacingAfter(80);

        XWPFRun run = createNormalRun(paragraph);

        for (int i = 0; i < items.size(); i++) {
            String item = items.get(i);
            run.setText("● " + item);

            if (i < items.size() - 1) {
                run.addBreak();
            }
        }
    }

    private List<String> parseKeyPointItems(String keyPointText) {
        List<String> items = new ArrayList<>();

        if (!hasText(keyPointText)) {
            return items;
        }

        String[] lines = keyPointText.replace("\r", "").split("\n");

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();

            if (line.isBlank()) {
                continue;
            }

            // 去掉模型可能生成的前导符号
            line = line.replaceFirst("^[·•●]\\s*", "");
            line = line.replaceFirst("^[-*]\\s*", "");
            line = line.trim();

            if (!line.isBlank()) {
                items.add(line);
            }
        }

        return items;
    }

    private List<SectionItem> parseSectionItems(String sectionText) {
        List<SectionItem> items = new ArrayList<>();

        if (!hasText(sectionText)) {
            return items;
        }

        String[] lines = sectionText.replace("\r", "").split("\n");

        String currentTitle = null;
        StringBuilder currentBody = new StringBuilder();

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();

            if (line.isBlank()) {
                continue;
            }

            if (isNumberedTitle(line)) {
                if (currentTitle != null) {
                    items.add(new SectionItem(currentTitle, currentBody.toString().trim()));
                }

                currentTitle = line;
                currentBody = new StringBuilder();
            } else {
                if (currentTitle == null) {
                    currentTitle = line;
                } else {
                    if (!currentBody.isEmpty()) {
                        currentBody.append("\n");
                    }
                    currentBody.append(line);
                }
            }
        }

        if (currentTitle != null) {
            items.add(new SectionItem(currentTitle, currentBody.toString().trim()));
        }

        return items;
    }

    private boolean isNumberedTitle(String line) {
        return line != null && line.trim().matches("^\\d+[\\.、．].+");
    }

    /**
     * 普通占位符替换：
     * 只替换文字 Run，不删除段落，不重建段落，避免破坏模板中的图片横线。
     */
    private void replaceNormalPlaceholders(XWPFDocument document, Map<String, String> placeholders) {
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            replaceNormalPlaceholdersInParagraph(paragraph, placeholders);
        }

        for (XWPFTable table : document.getTables()) {
            replaceNormalPlaceholdersInTable(table, placeholders);
        }
    }

    private void replaceNormalPlaceholdersInTable(XWPFTable table, Map<String, String> placeholders) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph paragraph : cell.getParagraphs()) {
                    replaceNormalPlaceholdersInParagraph(paragraph, placeholders);
                }

                for (XWPFTable nestedTable : cell.getTables()) {
                    replaceNormalPlaceholdersInTable(nestedTable, placeholders);
                }
            }
        }
    }

    private void replaceNormalPlaceholdersInParagraph(
            XWPFParagraph paragraph,
            Map<String, String> placeholders
    ) {
        List<XWPFRun> runs = paragraph.getRuns();

        if (runs == null || runs.isEmpty()) {
            return;
        }

        for (XWPFRun run : runs) {
            String text = run.getText(0);

            if (!hasText(text)) {
                continue;
            }

            String replacedText = text;

            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                replacedText = replacedText.replace(entry.getKey(), entry.getValue());
            }

            if (!replacedText.equals(text)) {
                replaceRunTextWithBreaks(run, replacedText);
            }
        }
    }

    private void replaceRunTextWithBreaks(XWPFRun run, String text) {
        if (text == null) {
            run.setText("", 0);
            return;
        }

        String[] lines = text.split("\\R", -1);

        if (lines.length == 0) {
            run.setText("", 0);
            return;
        }

        run.setText(lines[0], 0);

        for (int i = 1; i < lines.length; i++) {
            run.addBreak();
            run.setText(lines[i]);
        }
    }

    private void clearParagraph(XWPFParagraph paragraph) {
        List<XWPFRun> runs = paragraph.getRuns();

        if (runs == null) {
            return;
        }

        for (int i = runs.size() - 1; i >= 0; i--) {
            paragraph.removeRun(i);
        }
    }

    private XWPFRun createTitleRun(XWPFParagraph paragraph) {
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontFamily(FONT_FANGSONG_GB2312);
        run.setFontSize(FONT_SIZE_THIRD);
        return run;
    }

    private XWPFRun createNormalRun(XWPFParagraph paragraph) {
        XWPFRun run = paragraph.createRun();
        run.setBold(false);
        run.setFontFamily(FONT_FANGSONG_GB2312);
        run.setFontSize(FONT_SIZE_THIRD);
        return run;
    }

    private boolean isNationalPolicy(PolicyDocument document) {
        String sourceName = safe(document.getSourceName());
        String sourceUrl = safe(document.getSourceUrl());
        String category = safe(document.getCategory());

        return category.contains("国家")
                || category.contains("国家重点事项")
                || sourceName.contains("国务院")
                || sourceName.contains("工业和信息化部")
                || sourceName.contains("国家发展改革委")
                || sourceName.contains("国家数据局")
                || sourceName.contains("国家互联网信息办公室")
                || sourceName.contains("国家网信办")
                || sourceUrl.contains("miit.gov.cn")
                || sourceUrl.contains("ndrc.gov.cn")
                || sourceUrl.contains("nda.gov.cn")
                || sourceUrl.contains("cac.gov.cn");
    }

    private boolean isProvincialPolicy(PolicyDocument document) {
        String sourceName = safe(document.getSourceName());
        String sourceUrl = safe(document.getSourceUrl());
        String category = safe(document.getCategory());

        return category.contains("省内")
                || category.contains("山东")
                || category.contains("省内工作推进")
                || sourceName.contains("山东")
                || sourceUrl.contains("shandong.gov.cn")
                || sourceUrl.contains("gxt.shandong.gov.cn");
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private record SectionItem(String title, String body) {
    }
}