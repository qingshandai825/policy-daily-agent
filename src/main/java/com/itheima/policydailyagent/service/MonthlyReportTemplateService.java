package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.agent.dto.MonthlyPioneerContent;
import com.itheima.policydailyagent.agent.dto.MonthlyReportContent;
import com.itheima.policydailyagent.agent.dto.MonthlyReportItem;
import com.itheima.policydailyagent.agent.dto.MonthlyReportSection;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Service
public class MonthlyReportTemplateService {

    static final String TEMPLATE_PATH = "templates/monthly_report_template.docx";

    private static final String FONT_FANGSONG_GB2312 = "仿宋_GB2312";
    private static final int THIRD_SIZE = 16;
    private static final List<String> TEMPLATE_GUIDANCE_PREFIXES = List.of(
            "【栏目定位】",
            "【内容来源】",
            "【写作格式】",
            "【案例选取原则】",
            "【填报说明】"
    );

    public byte[] fill(MonthlyReportContent content) {
        if (content == null) {
            throw new IllegalArgumentException("月报结构化内容不能为空");
        }

        byte[] template = readTemplate();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(template));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            validateTemplate(document);
            fillMetadata(document, content);
            fillKeyPoints(document, content);
            fillSections(document, content);
            fillPioneer(document, content.pioneer());
            removeTemplateGuidance(document);
            validateFilledDocument(document);
            document.write(output);
            return output.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException("月报模板读取或写入失败", error);
        }
    }

    private byte[] readTemplate() {
        try {
            return new ClassPathResource(TEMPLATE_PATH).getContentAsByteArray();
        } catch (IOException error) {
            throw new IllegalStateException("找不到月报模板: " + TEMPLATE_PATH, error);
        }
    }

    private void validateTemplate(XWPFDocument document) {
        String text = documentText(document);
        List<String> required = List.of(
                "{{REPORT_MONTH}}",
                "{{KEY_POINTS_NATIONAL}}",
                "{{KEY_POINTS_PROVINCIAL}}",
                "{{KEY_POINTS_PIONEER}}",
                "{{KEY_POINTS_CASE}}",
                "{{KEY_POINTS_TREND}}",
                "{{SECTION_NATIONAL}}",
                "{{SECTION_PROVINCIAL}}",
                "{{SECTION_CASE}}",
                "{{SECTION_TREND}}",
                "{{REPORT_TO}}",
                "{{SEND_TO}}",
                "{{CONTACT_INFO}}"
        );
        List<String> missing = required.stream().filter(value -> !text.contains(value)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("月报模板缺少必要占位符: " + String.join(", ", missing));
        }
        if (document.getTables().size() != 1 || document.getTables().get(0).getNumberOfRows() != 4) {
            throw new IllegalStateException("月报模板附件表结构不符合预期");
        }
        if (countSections(document) != 4) {
            throw new IllegalStateException("月报模板必须保留4个分节");
        }
    }

    private void fillMetadata(XWPFDocument document, MonthlyReportContent content) {
        replaceWholeParagraph(
                document,
                text -> text.matches(".*第\\d+期，总第\\d+期.*"),
                "（%d年第%d期，总第%d期）".formatted(
                        content.reportYear(),
                        content.issueNo(),
                        content.totalIssueNo()
                )
        );
        replaceToken(document, "{{REPORT_MONTH}}", content.reportMonth());
        replaceToken(document, "{{REPORT_TO}}", safe(content.reportTo()));
        replaceToken(document, "{{SEND_TO}}", safe(content.sendTo()));
        replaceToken(document, "{{CONTACT_INFO}}", safe(content.contactInfo()));
        replaceText(document, "XX年XX月XX日", safe(content.statDate()));
    }

    private void fillKeyPoints(XWPFDocument document, MonthlyReportContent content) {
        replaceKeyPoints(document, "{{KEY_POINTS_NATIONAL}}", content.national().keyPoints());
        replaceKeyPoints(document, "{{KEY_POINTS_PROVINCIAL}}", content.provincial().keyPoints());
        replaceKeyPoints(document, "{{KEY_POINTS_PIONEER}}", content.pioneer().keyPoints());
        replaceKeyPoints(document, "{{KEY_POINTS_CASE}}", content.cases().keyPoints());
        replaceKeyPoints(document, "{{KEY_POINTS_TREND}}", content.trends().keyPoints());
    }

    private void fillSections(XWPFDocument document, MonthlyReportContent content) {
        replaceSection(document, "{{SECTION_NATIONAL}}", content.national());
        replaceSection(document, "{{SECTION_PROVINCIAL}}", content.provincial());
        replaceSection(document, "{{SECTION_CASE}}", content.cases());
        replaceSection(document, "{{SECTION_TREND}}", content.trends());
    }

    private void fillPioneer(XWPFDocument document, MonthlyPioneerContent pioneer) {
        List<String> values = List.of(
                defaultText(pioneer.overview()),
                defaultText(pioneer.metrics()),
                defaultText(pioneer.problems()),
                defaultText(pioneer.practices())
        );
        List<XWPFParagraph> targets = document.getParagraphs().stream()
                .filter(paragraph -> paragraph.getText().startsWith("【预期内容】"))
                .toList();
        if (targets.size() != values.size()) {
            throw new IllegalStateException("月报模板先锋应用正文槽位数量应为4个，实际为" + targets.size());
        }
        for (int i = 0; i < targets.size(); i++) {
            setParagraphText(targets.get(i), values.get(i), false);
        }
    }

    private void replaceKeyPoints(XWPFDocument document, String token, List<String> points) {
        XWPFParagraph paragraph = requireParagraph(document, token);
        CTRPr sourceRunProperties = copyFirstRunProperties(paragraph);
        clearRuns(paragraph);
        paragraph.setFirstLineIndent(0);

        List<String> values = points == null || points.isEmpty() ? List.of("材料未明确") : points;
        for (int i = 0; i < values.size(); i++) {
            XWPFRun run = paragraph.createRun();
            applyRunProperties(run, sourceRunProperties, false);
            run.setText("● " + defaultText(values.get(i)));
            if (i < values.size() - 1) {
                run.addBreak();
            }
        }
    }

    private void replaceSection(XWPFDocument document, String token, MonthlyReportSection section) {
        XWPFParagraph placeholder = requireParagraph(document, token);
        List<MonthlyReportItem> items = section == null || section.items().isEmpty()
                ? List.of(new MonthlyReportItem("本期情况", "材料未明确。"))
                : section.items();

        CTPPr paragraphProperties = copyParagraphProperties(placeholder);
        CTRPr runProperties = copyFirstRunProperties(placeholder);
        writeSectionItem(placeholder, items.get(0), 1, runProperties);

        XWPFParagraph previous = placeholder;
        for (int i = 1; i < items.size(); i++) {
            XWPFParagraph inserted = insertParagraphAfter(document, previous);
            if (paragraphProperties != null) {
                inserted.getCTP().setPPr((CTPPr) paragraphProperties.copy());
            }
            writeSectionItem(inserted, items.get(i), i + 1, runProperties);
            previous = inserted;
        }
    }

    private XWPFParagraph insertParagraphAfter(XWPFDocument document, XWPFParagraph paragraph) {
        try (XmlCursor cursor = paragraph.getCTP().newCursor()) {
            cursor.toEndToken();
            cursor.toNextToken();
            XWPFParagraph inserted = document.insertNewParagraph(cursor);
            if (inserted == null) {
                throw new IllegalStateException("无法在月报模板中插入栏目段落");
            }
            return inserted;
        }
    }

    private void writeSectionItem(
            XWPFParagraph paragraph,
            MonthlyReportItem item,
            int number,
            CTRPr sourceRunProperties
    ) {
        clearRuns(paragraph);
        paragraph.setFirstLineIndent(0);

        XWPFRun title = paragraph.createRun();
        applyRunProperties(title, sourceRunProperties, true);
        title.setText(number + ". " + defaultText(item.title()));
        title.addBreak();

        XWPFRun body = paragraph.createRun();
        applyRunProperties(body, sourceRunProperties, false);
        body.setText("　　" + defaultText(item.body()));
    }

    private void removeTemplateGuidance(XWPFDocument document) {
        List<IBodyElement> elements = new ArrayList<>(document.getBodyElements());
        for (int i = elements.size() - 1; i >= 0; i--) {
            IBodyElement element = elements.get(i);
            if (element instanceof XWPFParagraph paragraph
                    && TEMPLATE_GUIDANCE_PREFIXES.stream().anyMatch(paragraph.getText()::startsWith)) {
                document.removeBodyElement(i);
            }
        }
    }

    private void replaceWholeParagraph(
            XWPFDocument document,
            java.util.function.Predicate<String> predicate,
            String value
    ) {
        XWPFParagraph paragraph = document.getParagraphs().stream()
                .filter(item -> predicate.test(item.getText()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("月报模板中未找到期号段落"));
        setParagraphTextPreservingRunStyle(paragraph, value);
    }

    private void replaceToken(XWPFDocument document, String token, String value) {
        replaceText(document, token, safe(value));
    }

    private void replaceText(XWPFDocument document, String source, String replacement) {
        boolean replaced = false;
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            if (replaceTextInParagraph(paragraph, source, replacement)) {
                replaced = true;
            }
        }
        for (XWPFTable table : document.getTables()) {
            for (var row : table.getRows()) {
                for (var cell : row.getTableCells()) {
                    for (XWPFParagraph paragraph : cell.getParagraphs()) {
                        if (replaceTextInParagraph(paragraph, source, replacement)) {
                            replaced = true;
                        }
                    }
                }
            }
        }
        if (!replaced) {
            throw new IllegalStateException("月报模板中未找到文本: " + source);
        }
    }

    private boolean replaceTextInParagraph(XWPFParagraph paragraph, String source, String replacement) {
        for (XWPFRun run : paragraph.getRuns()) {
            String text = run.getText(0);
            if (text != null && text.contains(source)) {
                run.setText(text.replace(source, replacement), 0);
                return true;
            }
        }
        if (!paragraph.getText().contains(source)) {
            return false;
        }

        String replaced = paragraph.getText().replace(source, replacement);
        setParagraphText(paragraph, replaced, false);
        return true;
    }

    private XWPFParagraph requireParagraph(XWPFDocument document, String token) {
        return document.getParagraphs().stream()
                .filter(paragraph -> paragraph.getText().contains(token))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("月报模板中未找到占位段落: " + token));
    }

    private void setParagraphTextPreservingRunStyle(XWPFParagraph paragraph, String value) {
        CTRPr runProperties = copyFirstRunProperties(paragraph);
        clearRuns(paragraph);
        XWPFRun run = paragraph.createRun();
        if (runProperties != null) {
            run.getCTR().setRPr((CTRPr) runProperties.copy());
        }
        run.setText(defaultText(value));
    }

    private void setParagraphText(XWPFParagraph paragraph, String value, boolean preserveBold) {
        CTRPr runProperties = copyFirstRunProperties(paragraph);
        clearRuns(paragraph);
        XWPFRun run = paragraph.createRun();
        applyRunProperties(run, runProperties, preserveBold);
        run.setText(defaultText(value));
    }

    private void applyRunProperties(XWPFRun run, CTRPr source, boolean bold) {
        if (source != null) {
            run.getCTR().setRPr((CTRPr) source.copy());
        }
        run.setFontFamily(FONT_FANGSONG_GB2312);
        run.setFontSize(THIRD_SIZE);
        run.setBold(bold);
    }

    private CTPPr copyParagraphProperties(XWPFParagraph paragraph) {
        return paragraph.getCTP().isSetPPr() ? (CTPPr) paragraph.getCTP().getPPr().copy() : null;
    }

    private CTRPr copyFirstRunProperties(XWPFParagraph paragraph) {
        for (XWPFRun run : paragraph.getRuns()) {
            if (run.getCTR().isSetRPr()) {
                return (CTRPr) run.getCTR().getRPr().copy();
            }
        }
        return null;
    }

    private void clearRuns(XWPFParagraph paragraph) {
        for (int i = paragraph.getRuns().size() - 1; i >= 0; i--) {
            paragraph.removeRun(i);
        }
    }

    private void validateFilledDocument(XWPFDocument document) {
        String text = documentText(document);
        List<String> forbidden = List.of("{{", "【栏目定位】", "【预期内容】", "XX年XX月XX日");
        List<String> remaining = forbidden.stream().filter(text::contains).toList();
        if (!remaining.isEmpty()) {
            throw new IllegalStateException("月报仍包含模板占位内容: " + String.join(", ", remaining));
        }
        if (countSections(document) != 4 || document.getTables().size() != 1) {
            throw new IllegalStateException("填充月报时破坏了模板分节或附件表");
        }
    }

    private String documentText(XWPFDocument document) {
        StringBuilder builder = new StringBuilder();
        Consumer<XWPFParagraph> append = paragraph -> builder.append(paragraph.getText()).append('\n');
        document.getParagraphs().forEach(append);
        for (XWPFTable table : document.getTables()) {
            table.getRows().forEach(row -> row.getTableCells().forEach(cell -> cell.getParagraphs().forEach(append)));
        }
        return builder.toString();
    }

    private int countSections(XWPFDocument document) {
        int paragraphSections = (int) document.getParagraphs().stream()
                .filter(paragraph -> paragraph.getCTP().isSetPPr()
                        && paragraph.getCTP().getPPr().isSetSectPr())
                .count();
        return paragraphSections + (document.getDocument().getBody().isSetSectPr() ? 1 : 0);
    }

    private String defaultText(String value) {
        return value == null || value.isBlank() ? "材料未明确。" : value.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
