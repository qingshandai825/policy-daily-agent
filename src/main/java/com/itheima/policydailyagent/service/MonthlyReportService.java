package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.domain.report.*;
import com.itheima.policydailyagent.dto.MonthlyReportGenerateRequest;
import com.itheima.policydailyagent.dto.MonthlyReportGenerationResult;
import com.itheima.policydailyagent.dto.MonthlyReportGenerationView;
import com.itheima.policydailyagent.repository.MonthlyReportGenerationRepository;
import com.itheima.policydailyagent.repository.MonthlyReportItemRepository;
import com.itheima.policydailyagent.repository.MonthlyReportRepository;
import com.itheima.policydailyagent.repository.ReportSectionRepository;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MonthlyReportService {

    private static final String TEMPLATE_PATH = "templates/monthly_report_template.docx";
    private static final String TEMPLATE_VERSION = "v2-no-attachment";
    private static final String TEMPLATE_SHA256 = "28B6D1314C04521444089A5B623266FEDF674C3A77FE8DA893CA3F623E5508A2";
    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final Set<String> CONTENT_SECTION_CODES = Set.of(
            "NATIONAL",
            "PROVINCIAL",
            "PIONEER_OVERALL",
            "PIONEER_INDICATORS",
            "PIONEER_SUPPORT",
            "PIONEER_EXPERIENCE",
            "CASE",
            "TREND"
    );

    private final MonthlyReportRepository monthlyReportRepository;
    private final MonthlyReportItemRepository monthlyReportItemRepository;
    private final ReportSectionRepository reportSectionRepository;
    private final MonthlyReportGenerationRepository generationRepository;

    public MonthlyReportService(
            MonthlyReportRepository monthlyReportRepository,
            MonthlyReportItemRepository monthlyReportItemRepository,
            ReportSectionRepository reportSectionRepository,
            MonthlyReportGenerationRepository generationRepository
    ) {
        this.monthlyReportRepository = monthlyReportRepository;
        this.monthlyReportItemRepository = monthlyReportItemRepository;
        this.reportSectionRepository = reportSectionRepository;
        this.generationRepository = generationRepository;
    }

    /**
     * Word 生成是确定性步骤：只读取人工确认后的内容，不调用 LLM。
     */
    @Transactional
    public MonthlyReportGenerationResult generateMonthlyReport(MonthlyReportGenerateRequest request) {
        if (request == null || request.reportId() == null) {
            throw new IllegalArgumentException("reportId 不能为空");
        }
        validateGenerationRequest(request);

        MonthlyReport report = monthlyReportRepository.findByIdForUpdate(request.reportId())
                .orElseThrow(() -> new IllegalArgumentException("月报不存在，id=" + request.reportId()));

        if (report.getStatus() != MonthlyReportStatus.CONTENT_CONFIRMED) {
            throw new IllegalArgumentException("只有 CONTENT_CONFIRMED 状态的月报可以生成正式 Word");
        }

        List<MonthlyReportItem> items = monthlyReportItemRepository
                .findByReportIdAndStatusOrderBySectionIdAscSortOrderAsc(
                        report.getId(),
                        ReportItemStatus.CONFIRMED
                );

        if (items.isEmpty()) {
            throw new IllegalArgumentException("月报尚无已确认内容，不能生成正式 Word");
        }
        validateConfirmedItems(items);

        Map<Long, String> sectionCodes = reportSectionRepository.findAll().stream()
                .collect(Collectors.toMap(ReportSection::getId, ReportSection::getSectionCode));

        Map<String, List<MonthlyReportItem>> itemsBySection = items.stream()
                .collect(Collectors.groupingBy(
                        item -> sectionCodes.getOrDefault(item.getSectionId(), ""),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        validateSectionMappings(itemsBySection);

        Map<String, String> placeholders = buildPlaceholderMap(request, report, itemsBySection);
        byte[] fileBytes = fillTemplate(placeholders);
        String outputHash = sha256(fileBytes);
        String outputFileName = safeFileName(report.getTitle()) + ".docx";
        String reportMonth = report.getReportYear() + "年" + report.getReportMonth() + "月";

        MonthlyReportGeneration generation = new MonthlyReportGeneration();
        generation.setReportId(report.getId());
        generation.setGenerationNo(generationRepository
                .findTopByReportIdOrderByGenerationNoDesc(report.getId())
                .map(existing -> existing.getGenerationNo() + 1)
                .orElse(1));
        generation.setIssueNo(request.issueNo());
        generation.setTotalIssueNo(request.totalIssueNo());
        generation.setReportMonth(reportMonth);
        generation.setReportTo(request.reportTo().trim());
        generation.setSendTo(request.sendTo().trim());
        generation.setContactInfo(request.contactInfo().trim());
        generation.setGeneratedBy(request.generatedBy().trim());
        generation.setFileName(outputFileName);
        generation.setContentType(DOCX_CONTENT_TYPE);
        generation.setFileSize(fileBytes.length);
        generation.setSha256(outputHash);
        generation.setFileContent(fileBytes);
        generation = generationRepository.save(generation);

        report.setStatus(MonthlyReportStatus.GENERATED);
        report.setGeneratedAt(LocalDateTime.now());
        report.setTemplateVersion(TEMPLATE_VERSION);
        report.setTemplateHash(TEMPLATE_SHA256);
        report.setOutputFileName(outputFileName);
        report.setOutputHash(outputHash);
        monthlyReportRepository.save(report);

        return resultOf(generation);
    }

    @Transactional(readOnly = true)
    public List<MonthlyReportGenerationView> listGenerations(Long reportId) {
        if (!monthlyReportRepository.existsById(reportId)) {
            throw new IllegalArgumentException("月报不存在，id=" + reportId);
        }
        return generationRepository.findByReportIdOrderByGenerationNoDesc(reportId).stream()
                .map(this::viewOf)
                .toList();
    }

    @Transactional(readOnly = true)
    public MonthlyReportGenerationResult downloadGeneration(Long reportId, Long generationId) {
        if (!monthlyReportRepository.existsById(reportId)) {
            throw new IllegalArgumentException("月报不存在，id=" + reportId);
        }
        MonthlyReportGeneration generation = generationRepository
                .findByIdAndReportId(generationId, reportId)
                .orElseThrow(() -> new IllegalArgumentException("生成档案不存在或不属于当前月报"));
        return resultOf(generation);
    }

    private Map<String, String> buildPlaceholderMap(
            MonthlyReportGenerateRequest request,
            MonthlyReport report,
            Map<String, List<MonthlyReportItem>> itemsBySection
    ) {
        if (request.issueNo() == null || request.totalIssueNo() == null) {
            throw new IllegalArgumentException("期号和总期号不能为空");
        }

        Map<String, String> values = new LinkedHashMap<>();
        values.put("{{REPORT_YEAR}}", String.valueOf(report.getReportYear()));
        values.put("{{ISSUE_NO}}", String.valueOf(request.issueNo()));
        values.put("{{TOTAL_ISSUE_NO}}", String.valueOf(request.totalIssueNo()));
        values.put("{{REPORT_MONTH}}", report.getReportYear() + "年" + report.getReportMonth() + "月");

        values.put("{{KEY_POINTS_NATIONAL}}", keyPoints(itemsBySection.get("NATIONAL")));
        values.put("{{KEY_POINTS_PROVINCIAL}}", keyPoints(itemsBySection.get("PROVINCIAL")));
        values.put("{{KEY_POINTS_PIONEER}}", keyPoints(mergePioneerItems(itemsBySection)));
        values.put("{{KEY_POINTS_CASE}}", keyPoints(itemsBySection.get("CASE")));
        values.put("{{KEY_POINTS_TREND}}", keyPoints(itemsBySection.get("TREND")));

        values.put("{{SECTION_NATIONAL}}", sectionContent(itemsBySection.get("NATIONAL")));
        values.put("{{SECTION_PROVINCIAL}}", sectionContent(itemsBySection.get("PROVINCIAL")));
        values.put("{{SECTION_PIONEER_OVERALL}}", sectionContent(itemsBySection.get("PIONEER_OVERALL")));
        values.put("{{SECTION_PIONEER_INDICATORS}}", sectionContent(itemsBySection.get("PIONEER_INDICATORS")));
        values.put("{{SECTION_PIONEER_SUPPORT}}", sectionContent(itemsBySection.get("PIONEER_SUPPORT")));
        values.put("{{SECTION_PIONEER_EXPERIENCE}}", sectionContent(itemsBySection.get("PIONEER_EXPERIENCE")));
        values.put("{{SECTION_CASE}}", sectionContent(itemsBySection.get("CASE")));
        values.put("{{SECTION_TREND}}", sectionContent(itemsBySection.get("TREND")));

        values.put("{{REPORT_TO}}", requireText(request.reportTo(), "报送单位"));
        values.put("{{SEND_TO}}", requireText(request.sendTo(), "抄送单位"));
        values.put("{{CONTACT_INFO}}", requireText(request.contactInfo(), "联系人及联系方式"));
        return values;
    }

    private byte[] fillTemplate(Map<String, String> placeholders) {
        try (InputStream input = new ClassPathResource(TEMPLATE_PATH).getInputStream();
             XWPFDocument document = new XWPFDocument(input);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            removeWritingGuideParagraphs(document);
            replacePlaceholders(document, placeholders);
            assertNoPlaceholders(document);
            document.getProperties().getCoreProperties().setTitle("山东省人工智能赋能制造业工作月报");
            document.getProperties().getCoreProperties().setCreator("Policy Monthly Report Agent");
            document.write(output);
            return output.toByteArray();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Word 模板填充失败：" + e.getMessage(), e);
        }
    }

    private void removeWritingGuideParagraphs(XWPFDocument document) {
        for (int index = document.getBodyElements().size() - 1; index >= 0; index--) {
            IBodyElement element = document.getBodyElements().get(index);
            if (element instanceof XWPFParagraph paragraph && isWritingGuide(paragraph.getText())) {
                document.removeBodyElement(index);
            }
        }
    }

    private boolean isWritingGuide(String text) {
        if (!hasText(text)) {
            return false;
        }
        String normalized = text.trim();
        if (normalized.contains("{{") || normalized.contains("}}")) {
            return false;
        }
        return normalized.startsWith("【栏目定位】")
                || normalized.startsWith("【内容来源】")
                || normalized.startsWith("【写作格式】")
                || normalized.startsWith("【预期内容】")
                || normalized.startsWith("【案例选取原则】")
                || normalized.startsWith("【填报说明】")
                || (normalized.startsWith("（") && normalized.endsWith("）"))
                || (normalized.startsWith("(") && normalized.endsWith(")"));
    }

    private void replacePlaceholders(XWPFDocument document, Map<String, String> placeholders) {
        document.getParagraphs().forEach(paragraph -> replaceInParagraph(paragraph, placeholders));
        document.getTables().forEach(table -> replaceInTable(table, placeholders));
        document.getHeaderList().forEach(header -> {
            header.getParagraphs().forEach(paragraph -> replaceInParagraph(paragraph, placeholders));
            header.getTables().forEach(table -> replaceInTable(table, placeholders));
        });
        document.getFooterList().forEach(footer -> {
            footer.getParagraphs().forEach(paragraph -> replaceInParagraph(paragraph, placeholders));
            footer.getTables().forEach(table -> replaceInTable(table, placeholders));
        });
    }

    private void replaceInTable(XWPFTable table, Map<String, String> placeholders) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                cell.getParagraphs().forEach(paragraph -> replaceInParagraph(paragraph, placeholders));
                cell.getTables().forEach(nested -> replaceInTable(nested, placeholders));
            }
        }
    }

    private void replaceInParagraph(XWPFParagraph paragraph, Map<String, String> placeholders) {
        for (XWPFRun run : paragraph.getRuns()) {
            String current = run.getText(0);
            if (current == null) {
                continue;
            }
            String replacement = current;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                replacement = replacement.replace(entry.getKey(), nullToEmpty(entry.getValue()));
            }
            if (!replacement.equals(current)) {
                setRunText(run, replacement);
            }
        }
    }

    private void setRunText(XWPFRun run, String value) {
        String[] lines = nullToEmpty(value).split("\\R", -1);
        run.setText(lines.length == 0 ? "" : lines[0], 0);
        for (int index = 1; index < lines.length; index++) {
            run.addBreak();
            run.setText(lines[index]);
        }
    }

    private void assertNoPlaceholders(XWPFDocument document) {
        List<String> remaining = new ArrayList<>();
        document.getParagraphs().forEach(p -> collectPlaceholder(p.getText(), remaining));
        document.getTables().forEach(t -> collectTablePlaceholders(t, remaining));
        document.getHeaderList().forEach(h -> h.getParagraphs().forEach(p -> collectPlaceholder(p.getText(), remaining)));
        document.getFooterList().forEach(f -> f.getParagraphs().forEach(p -> collectPlaceholder(p.getText(), remaining)));
        if (!remaining.isEmpty()) {
            throw new IllegalArgumentException("模板仍有未填充占位符：" + String.join(", ", remaining));
        }
    }

    private void collectTablePlaceholders(XWPFTable table, List<String> remaining) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                collectPlaceholder(cell.getText(), remaining);
                cell.getTables().forEach(nested -> collectTablePlaceholders(nested, remaining));
            }
        }
    }

    private void collectPlaceholder(String text, List<String> remaining) {
        if (text != null && text.contains("{{") && text.contains("}}")) {
            remaining.add(text);
        }
    }

    private List<MonthlyReportItem> mergePioneerItems(
            Map<String, List<MonthlyReportItem>> itemsBySection
    ) {
        return List.of(
                        "PIONEER_OVERALL",
                        "PIONEER_INDICATORS",
                        "PIONEER_SUPPORT",
                        "PIONEER_EXPERIENCE"
                ).stream()
                .flatMap(code -> itemsBySection.getOrDefault(code, List.of()).stream())
                .toList();
    }

    private void validateSectionMappings(Map<String, List<MonthlyReportItem>> itemsBySection) {
        List<String> unsupportedSectionCodes = itemsBySection.keySet().stream()
                .filter(code -> !CONTENT_SECTION_CODES.contains(code))
                .toList();
        if (!unsupportedSectionCodes.isEmpty()) {
            throw new IllegalArgumentException("存在无法映射到 Word 插槽的栏目：" + unsupportedSectionCodes);
        }
    }

    private String keyPoints(List<MonthlyReportItem> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        return items.stream()
                .map(item -> "● " + requireText(item.getItemTitle(), "本期要目标题"))
                .collect(Collectors.joining("\n"));
    }

    private String sectionContent(List<MonthlyReportItem> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder content = new StringBuilder();
        for (int index = 0; index < items.size(); index++) {
            MonthlyReportItem item = items.get(index);
            if (index > 0) {
                content.append("\n\n");
            }
            content.append(index + 1)
                    .append(".")
                    .append(requireText(item.getItemTitle(), "月报内容标题"))
                    .append("\n")
                    .append(requireText(item.getFinalContent(), "月报最终内容"));
        }
        return content.toString();
    }

    private void validateConfirmedItems(List<MonthlyReportItem> items) {
        for (MonthlyReportItem item : items) {
            requireText(item.getItemTitle(), "月报内容标题");
            requireText(item.getFinalContent(), "月报最终内容");
        }
    }

    private void validateGenerationRequest(MonthlyReportGenerateRequest request) {
        if (request.issueNo() == null || request.issueNo() < 1) {
            throw new IllegalArgumentException("当年期号必须大于 0");
        }
        if (request.totalIssueNo() == null || request.totalIssueNo() < request.issueNo()) {
            throw new IllegalArgumentException("总期号不能小于当年期号");
        }
        requireText(request.reportTo(), "报送单位");
        requireText(request.sendTo(), "抄送单位");
        requireText(request.contactInfo(), "联系人及联系方式");
        requireText(request.generatedBy(), "生成操作人");
    }

    private MonthlyReportGenerationResult resultOf(MonthlyReportGeneration generation) {
        return new MonthlyReportGenerationResult(
                generation.getId(),
                generation.getReportId(),
                generation.getGenerationNo(),
                generation.getFileName(),
                generation.getContentType(),
                generation.getFileSize(),
                generation.getSha256(),
                generation.getFileContent()
        );
    }

    private MonthlyReportGenerationView viewOf(MonthlyReportGeneration generation) {
        return new MonthlyReportGenerationView(
                generation.getId(),
                generation.getReportId(),
                generation.getGenerationNo(),
                generation.getIssueNo(),
                generation.getTotalIssueNo(),
                generation.getReportMonth(),
                generation.getReportTo(),
                generation.getSendTo(),
                generation.getContactInfo(),
                generation.getGeneratedBy(),
                generation.getFileName(),
                generation.getFileSize(),
                generation.getSha256(),
                generation.getCreatedAt()
        );
    }

    private String safeFileName(String value) {
        String safe = requireText(value, "月报标题")
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .trim();
        return safe.isEmpty() ? "人工智能政策月报" : safe;
    }
    private String requireText(String value, String fieldName) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("无法计算 Word 文件哈希", e);
        }
    }
}
