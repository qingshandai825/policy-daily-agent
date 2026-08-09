package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.entity.DailyTask;
import com.itheima.policydailyagent.entity.PolicyDocument;
import com.itheima.policydailyagent.repository.DailyTaskRepository;
import com.itheima.policydailyagent.repository.PolicyDocumentRepository;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DailyReportService {

    private static final String FONT_SONG = "宋体";
    private static final String FONT_FANGSONG = "仿宋_GB2312";
    private static final String FONT_HEITI = "黑体";
    private static final int MAX_BODY_LENGTH = 800;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年M月d日");

    private final PolicyDocumentRepository policyDocumentRepository;
    private final DailyTaskRepository dailyTaskRepository;

    public DailyReportService(
            PolicyDocumentRepository policyDocumentRepository,
            DailyTaskRepository dailyTaskRepository
    ) {
        this.policyDocumentRepository = policyDocumentRepository;
        this.dailyTaskRepository = dailyTaskRepository;
    }

    public byte[] generateDailyReport(Long taskId) {
        DailyTask task = dailyTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Daily task not found: " + taskId));

        List<PolicyDocument> documents = policyDocumentRepository
                .findByDailyTaskIdAndReviewStatusOrderByPublishDateDescCreatedAtDesc(
                        taskId,
                        PolicyReviewService.APPROVED
                )
                .stream()
                .filter(policy -> isWithinTaskDateRange(policy, task))
                .toList();

        if (documents.isEmpty()) {
            throw new IllegalArgumentException(
                    "No approved policy documents fall within the task date range for taskId=" + taskId
            );
        }

        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            configurePage(document);
            configureProperties(document, task);
            writeHeader(document, task, documents.size());
            writeKeyPoints(document, documents);
            writePolicySections(document, documents);
            writeEvidenceNotice(document);
            writeFooter(document);

            document.write(outputStream);
            return outputStream.toByteArray();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Daily Word report generation failed: " + e.getClass().getSimpleName() + " - " + e.getMessage(),
                    e
            );
        }
    }

    private void configurePage(XWPFDocument document) {
        CTSectPr section = document.getDocument().getBody().addNewSectPr();
        CTPageSz pageSize = section.addNewPgSz();
        pageSize.setW(BigInteger.valueOf(11906));
        pageSize.setH(BigInteger.valueOf(16838));

        CTPageMar margins = section.addNewPgMar();
        margins.setTop(BigInteger.valueOf(1440));
        margins.setBottom(BigInteger.valueOf(1440));
        margins.setLeft(BigInteger.valueOf(1701));
        margins.setRight(BigInteger.valueOf(1701));
        margins.setHeader(BigInteger.valueOf(720));
        margins.setFooter(BigInteger.valueOf(720));
    }

    private void configureProperties(XWPFDocument document, DailyTask task) {
        document.getProperties().getCoreProperties().setTitle("政策日报");
        document.getProperties().getCoreProperties().setCreator("政策日报智能体");
    }

    private void writeHeader(XWPFDocument document, DailyTask task, int approvedCount) {
        XWPFParagraph title = document.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        title.setSpacingAfter(160);
        addRun(title, "政策日报", FONT_SONG, 22, true, "C00000");

        XWPFParagraph taskName = document.createParagraph();
        taskName.setAlignment(ParagraphAlignment.CENTER);
        taskName.setSpacingAfter(120);
        addRun(taskName, safe(task.getTaskName(), "政策信息采集任务"), FONT_HEITI, 14, false, "222222");

        XWPFParagraph metadata = document.createParagraph();
        metadata.setAlignment(ParagraphAlignment.CENTER);
        metadata.setSpacingAfter(260);
        addRun(
                metadata,
                "统计范围：" + formatDateRange(task) + "    审核通过：" + approvedCount + "条",
                FONT_FANGSONG,
                12,
                false,
                "555555"
        );
    }

    private void writeKeyPoints(XWPFDocument document, List<PolicyDocument> documents) {
        addSectionHeading(document, "一、本期要点");

        for (PolicyDocument policy : documents) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setIndentationLeft(420);
            paragraph.setFirstLineIndent(-300);
            paragraph.setSpacingAfter(80);
            setLineSpacing(paragraph, 360);
            addRun(paragraph, "● ", FONT_FANGSONG, 14, false, "C00000");
            addRun(paragraph, safe(policy.getTitle(), "未命名政策"), FONT_FANGSONG, 14, false, "222222");
            if (hasText(policy.getSourceName())) {
                addRun(paragraph, "（" + policy.getSourceName().trim() + "）", FONT_FANGSONG, 12, false, "666666");
            }
        }
    }

    private void writePolicySections(XWPFDocument document, List<PolicyDocument> documents) {
        Map<String, List<PolicyDocument>> groups = groupDocuments(documents);
        String[] sectionNumbers = {"二", "三", "四"};
        int sectionIndex = 0;

        for (Map.Entry<String, List<PolicyDocument>> entry : groups.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }

            addSectionHeading(document, sectionNumbers[sectionIndex] + "、" + entry.getKey());
            sectionIndex++;

            int itemIndex = 1;
            for (PolicyDocument policy : entry.getValue()) {
                writePolicyItem(document, policy, itemIndex++);
            }
        }
    }

    private void writePolicyItem(XWPFDocument document, PolicyDocument policy, int index) {
        XWPFParagraph title = document.createParagraph();
        keepWithNext(title);
        title.setSpacingBefore(100);
        title.setSpacingAfter(80);
        setLineSpacing(title, 360);
        addRun(
                title,
                index + ". " + safe(policy.getTitle(), "未命名政策"),
                FONT_HEITI,
                15,
                true,
                "222222"
        );

        XWPFParagraph metadata = document.createParagraph();
        metadata.setSpacingAfter(80);
        setLineSpacing(metadata, 320);
        addRun(metadata, "发布日期：", FONT_FANGSONG, 12, true, "555555");
        addRun(metadata, formatDate(policy.getPublishDate()), FONT_FANGSONG, 12, false, "555555");
        addRun(metadata, "    发布单位：", FONT_FANGSONG, 12, true, "555555");
        addRun(metadata, safe(policy.getSourceName(), "待核验"), FONT_FANGSONG, 12, false, "555555");
        if (hasText(policy.getCategory())) {
            addRun(metadata, "    类别：", FONT_FANGSONG, 12, true, "555555");
            addRun(metadata, policy.getCategory().trim(), FONT_FANGSONG, 12, false, "555555");
        }

        XWPFParagraph body = document.createParagraph();
        body.setFirstLineIndent(560);
        body.setSpacingAfter(80);
        setLineSpacing(body, 420);
        addRun(body, "核心内容：", FONT_FANGSONG, 14, true, "222222");
        addRun(body, buildBodyText(policy), FONT_FANGSONG, 14, false, "222222");

        XWPFParagraph source = document.createParagraph();
        source.setSpacingAfter(160);
        setLineSpacing(source, 320);
        addRun(source, "原始来源：", FONT_FANGSONG, 12, true, "555555");
        if (hasText(policy.getSourceUrl())) {
            XWPFHyperlinkRun link = source.createHyperlinkRun(policy.getSourceUrl().trim());
            link.setText(policy.getSourceUrl().trim());
            link.setColor("0563C1");
            link.setUnderline(UnderlinePatterns.SINGLE);
            applyFont(link, FONT_FANGSONG, 11);
        } else {
            addRun(source, "待核验", FONT_FANGSONG, 12, false, "555555");
        }
    }

    private void writeEvidenceNotice(XWPFDocument document) {
        XWPFParagraph notice = document.createParagraph();
        notice.setSpacingBefore(160);
        notice.setSpacingAfter(80);
        setLineSpacing(notice, 320);
        addRun(notice, "说明：", FONT_FANGSONG, 11, true, "666666");
        addRun(
                notice,
                "本日报仅收录人工审核状态为“已通过”的政策信息；核心内容优先采用审核后的摘要，摘要为空时采用原文证据或正文节选。",
                FONT_FANGSONG,
                11,
                false,
                "666666"
        );
    }

    private void writeFooter(XWPFDocument document) {
        XWPFFooter footer = document.createFooter(HeaderFooterType.DEFAULT);
        XWPFParagraph paragraph = footer.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        addRun(
                paragraph,
                "政策日报智能体生成  |  " + LocalDate.now().format(DATE_FORMATTER),
                FONT_SONG,
                9,
                false,
                "777777"
        );
    }

    private void addSectionHeading(XWPFDocument document, String text) {
        XWPFParagraph heading = document.createParagraph();
        keepWithNext(heading);
        heading.setSpacingBefore(180);
        heading.setSpacingAfter(100);
        addRun(heading, text, FONT_HEITI, 16, true, "C00000");
    }

    private Map<String, List<PolicyDocument>> groupDocuments(List<PolicyDocument> documents) {
        Map<String, List<PolicyDocument>> groups = new LinkedHashMap<>();
        groups.put("国家政策与动态", new ArrayList<>());
        groups.put("地方政策与动态", new ArrayList<>());
        groups.put("其他政策信息", new ArrayList<>());

        for (PolicyDocument document : documents) {
            if (isNational(document)) {
                groups.get("国家政策与动态").add(document);
            } else if (isLocal(document)) {
                groups.get("地方政策与动态").add(document);
            } else {
                groups.get("其他政策信息").add(document);
            }
        }

        return groups;
    }

    private boolean isNational(PolicyDocument document) {
        String authority = safe(document.getAuthorityLevel(), "");
        String source = safe(document.getSourceName(), "");
        String url = safe(document.getSourceUrl(), "");
        String category = safe(document.getCategory(), "");
        return authority.contains("NATIONAL")
                || category.contains("国家")
                || source.contains("国务院")
                || source.contains("国家")
                || source.contains("工业和信息化部")
                || url.contains("gov.cn")
                || url.contains("miit.gov.cn")
                || url.contains("ndrc.gov.cn");
    }

    private boolean isLocal(PolicyDocument document) {
        String authority = safe(document.getAuthorityLevel(), "");
        String source = safe(document.getSourceName(), "");
        String category = safe(document.getCategory(), "");
        return authority.contains("LOCAL")
                || authority.contains("PROVINCIAL")
                || category.contains("省")
                || category.contains("地方")
                || source.contains("省")
                || source.contains("市")
                || source.contains("自治区");
    }

    private String buildBodyText(PolicyDocument policy) {
        String text;
        if (hasText(policy.getSummary())) {
            text = policy.getSummary();
        } else if (hasText(policy.getEvidenceSnippet())) {
            text = policy.getEvidenceSnippet();
        } else if (hasText(policy.getContent())) {
            text = policy.getContent();
        } else {
            return "原文内容未抓取，请通过来源链接复核。";
        }

        String normalized = normalizeWhitespace(text);
        String title = normalizeWhitespace(policy.getTitle());
        if (hasText(title) && normalized.startsWith(title)) {
            normalized = normalized.substring(title.length()).trim();
        }
        if (normalized.isBlank()) {
            return "原文内容未抓取，请通过来源链接复核。";
        }
        if (normalized.length() > MAX_BODY_LENGTH) {
            return normalized.substring(0, MAX_BODY_LENGTH).trim() + "……";
        }
        return normalized;
    }

    private boolean isWithinTaskDateRange(PolicyDocument policy, DailyTask task) {
        if (policy.getPublishDate() == null) {
            return false;
        }
        LocalDate start = resolvedTaskStartDate(task);
        LocalDate end = resolvedTaskEndDate(task);
        return !policy.getPublishDate().isBefore(start) && !policy.getPublishDate().isAfter(end);
    }

    private LocalDate resolvedTaskStartDate(DailyTask task) {
        if (task.getTargetStartDate() != null) {
            return task.getTargetStartDate();
        }
        return task.getTargetEndDate() != null ? task.getTargetEndDate() : LocalDate.now();
    }

    private LocalDate resolvedTaskEndDate(DailyTask task) {
        if (task.getTargetEndDate() != null) {
            return task.getTargetEndDate();
        }
        return task.getTargetStartDate() != null ? task.getTargetStartDate() : LocalDate.now();
    }

    private String formatDateRange(DailyTask task) {
        LocalDate start = resolvedTaskStartDate(task);
        LocalDate end = resolvedTaskEndDate(task);
        if (start.equals(end)) {
            return formatDate(start);
        }
        return formatDate(start) + "至" + formatDate(end);
    }

    private String formatDate(LocalDate date) {
        return date == null ? "待核验" : date.format(DATE_FORMATTER);
    }

    private String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace('\u00A0', ' ')
                .replace('\u3000', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private XWPFRun addRun(
            XWPFParagraph paragraph,
            String text,
            String font,
            int fontSize,
            boolean bold,
            String color
    ) {
        XWPFRun run = paragraph.createRun();
        run.setText(text == null ? "" : text);
        run.setBold(bold);
        run.setColor(color);
        applyFont(run, font, fontSize);
        return run;
    }

    private void applyFont(XWPFRun run, String font, int fontSize) {
        run.setFontFamily(font);
        run.setFontSize(fontSize);
        run.setFontFamily(font, XWPFRun.FontCharRange.eastAsia);
    }

    private void keepWithNext(XWPFParagraph paragraph) {
        if (!paragraph.getCTP().isSetPPr()) {
            paragraph.getCTP().addNewPPr();
        }
        paragraph.setKeepNext(true);
    }

    private void setLineSpacing(XWPFParagraph paragraph, int lineTwips) {
        if (!paragraph.getCTP().isSetPPr()) {
            paragraph.getCTP().addNewPPr();
        }
        CTSpacing spacing = paragraph.getCTP().getPPr().isSetSpacing()
                ? paragraph.getCTP().getPPr().getSpacing()
                : paragraph.getCTP().getPPr().addNewSpacing();
        spacing.setLine(BigInteger.valueOf(lineTwips));
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
