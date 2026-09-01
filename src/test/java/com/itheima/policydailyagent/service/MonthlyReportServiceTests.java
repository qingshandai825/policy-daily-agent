package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.domain.report.*;
import com.itheima.policydailyagent.dto.MonthlyReportGenerateRequest;
import com.itheima.policydailyagent.repository.MonthlyReportGenerationRepository;
import com.itheima.policydailyagent.repository.MonthlyReportItemRepository;
import com.itheima.policydailyagent.repository.MonthlyReportRepository;
import com.itheima.policydailyagent.repository.ReportSectionRepository;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MonthlyReportServiceTests {

    @Test
    void shouldRejectGenerationBeforeHumanContentConfirmation() {
        MonthlyReportRepository reportRepository = mock(MonthlyReportRepository.class);
        MonthlyReport report = report(MonthlyReportStatus.DRAFT);
        when(reportRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(report));

        MonthlyReportService service = new MonthlyReportService(
                reportRepository,
                mock(MonthlyReportItemRepository.class),
                mock(ReportSectionRepository.class),
                mock(MonthlyReportGenerationRepository.class)
        );

        assertThatThrownBy(() -> service.generateMonthlyReport(request()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CONTENT_CONFIRMED");
    }

    @Test
    void shouldRejectConfirmedItemsMappedToContainerSection() {
        MonthlyReportRepository reportRepository = mock(MonthlyReportRepository.class);
        MonthlyReportItemRepository itemRepository = mock(MonthlyReportItemRepository.class);
        ReportSectionRepository sectionRepository = mock(ReportSectionRepository.class);

        MonthlyReport report = report(MonthlyReportStatus.CONTENT_CONFIRMED);
        MonthlyReportItem item = new MonthlyReportItem();
        item.setReportId(1L);
        item.setSectionId(200L);
        item.setStatus(ReportItemStatus.CONFIRMED);
        item.setSourceType(ReportSourceType.POLICY);
        item.setItemTitle("先锋应用动态");
        item.setFinalContent("该内容被错误分配到了栏目容器。");

        ReportSection section = new ReportSection();
        section.setId(200L);
        section.setSectionCode("PIONEER_PROGRESS");

        when(reportRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(report));
        when(itemRepository.findByReportIdAndStatusOrderBySectionIdAscSortOrderAsc(
                1L,
                ReportItemStatus.CONFIRMED
        )).thenReturn(List.of(item));
        when(sectionRepository.findAll()).thenReturn(List.of(section));

        MonthlyReportService service = new MonthlyReportService(
                reportRepository,
                itemRepository,
                sectionRepository,
                mock(MonthlyReportGenerationRepository.class)
        );

        assertThatThrownBy(() -> service.generateMonthlyReport(request()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PIONEER_PROGRESS");
    }

    @Test
    void shouldFillOriginalTemplateAndArchiveDownloadableGeneration() throws Exception {
        MonthlyReportRepository reportRepository = mock(MonthlyReportRepository.class);
        MonthlyReportItemRepository itemRepository = mock(MonthlyReportItemRepository.class);
        ReportSectionRepository sectionRepository = mock(ReportSectionRepository.class);
        MonthlyReportGenerationRepository generationRepository = mock(MonthlyReportGenerationRepository.class);

        MonthlyReport report = report(MonthlyReportStatus.CONTENT_CONFIRMED);
        List<MonthlyReportItem> items = List.of(
                item(10L, 100L, "工业和信息化部部署人工智能赋能制造业工作", "2026年8月，工业和信息化部围绕人工智能赋能制造业作出工作部署。"),
                item(11L, 101L, "山东省推进制造业数字化转型", "山东省围绕制造业数字化转型组织供需对接和能力提升工作。"),
                item(12L, 102L, "先锋应用城市总体推进", "本月重点城市持续推进人工智能赋能制造业场景开放和项目落地。"),
                item(13L, 103L, "重点指标稳步提升", "重点企业改造、人工智能普及和产品培育等指标按计划推进。"),
                item(14L, 104L, "问题台账持续闭环", "针对供需对接和资源保障问题建立台账，推进分类帮扶。"),
                item(15L, 105L, "典型经验加快复制", "有关城市聚焦重点行业形成可复制的人工智能赋能制造模式。"),
                item(16L, 106L, "工业大模型赋能质量检测", "省内企业应用工业大模型提升质量检测效率，形成可量化成效。"),
                item(17L, 107L, "行业智能体加速落地", "行业智能体、高质量数据集与智能制造平台协同发展趋势进一步显现。")
        );
        List<ReportSection> sections = List.of(
                section(100L, "NATIONAL"),
                section(101L, "PROVINCIAL"),
                section(102L, "PIONEER_OVERALL"),
                section(103L, "PIONEER_INDICATORS"),
                section(104L, "PIONEER_SUPPORT"),
                section(105L, "PIONEER_EXPERIENCE"),
                section(106L, "CASE"),
                section(107L, "TREND")
        );

        when(reportRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(report));
        when(itemRepository.findByReportIdAndStatusOrderBySectionIdAscSortOrderAsc(
                1L,
                ReportItemStatus.CONFIRMED
        )).thenReturn(items);
        when(sectionRepository.findAll()).thenReturn(sections);
        when(generationRepository.findTopByReportIdOrderByGenerationNoDesc(1L))
                .thenReturn(Optional.empty());
        MonthlyReportGeneration[] archivedGeneration = new MonthlyReportGeneration[1];
        when(generationRepository.save(any(MonthlyReportGeneration.class))).thenAnswer(invocation -> {
            MonthlyReportGeneration generation = invocation.getArgument(0);
            generation.setId(501L);
            archivedGeneration[0] = generation;
            return generation;
        });
        MonthlyReportService service = new MonthlyReportService(
                reportRepository,
                itemRepository,
                sectionRepository,
                generationRepository
        );

        var generationResult = service.generateMonthlyReport(request());
        byte[] bytes = generationResult.content();

        assertThat(bytes).isNotEmpty();
        assertThat(report.getStatus()).isEqualTo(MonthlyReportStatus.GENERATED);
        assertThat(report.getTemplateVersion()).isEqualTo("v2-no-attachment");
        assertThat(report.getTemplateHash()).hasSize(64);
        assertThat(generationResult.generationId()).isEqualTo(501L);
        assertThat(generationResult.sha256()).hasSize(64);
        assertThat(generationResult.fileName()).isEqualTo("2026年8月人工智能政策月报.docx");

        when(reportRepository.existsById(1L)).thenReturn(true);
        when(generationRepository.findByIdAndReportId(501L, 1L))
                .thenReturn(Optional.of(archivedGeneration[0]));
        when(generationRepository.findByReportIdOrderByGenerationNoDesc(1L))
                .thenReturn(List.of(archivedGeneration[0]));

        var downloaded = service.downloadGeneration(1L, 501L);
        assertThat(downloaded.content()).isEqualTo(bytes);
        assertThat(downloaded.sha256()).isEqualTo(generationResult.sha256());
        assertThat(service.listGenerations(1L))
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.generationNo()).isEqualTo(1);
                    assertThat(view.generatedBy()).isEqualTo("qa-reviewer");
                    assertThat(view.sha256()).isEqualTo(generationResult.sha256());
                });

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            String text = document.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText())
                    .reduce("", (left, right) -> left + "\n" + right);

            assertThat(text)
                    .contains("工业和信息化部部署人工智能赋能制造业工作")
                    .doesNotContain("{{", "【栏目定位】", "【写作格式】", "【预期内容】", "附件1");
            assertThat(document.getTables()).isEmpty();
        }

        String qaOutput = System.getProperty("monthly.report.qa.output");
        if (qaOutput != null && !qaOutput.isBlank()) {
            Path output = Path.of(qaOutput);
            Files.createDirectories(output.getParent());
            Files.write(output, bytes);
        }
    }

    @Test
    void shouldRejectTotalIssueNumberBelowAnnualIssueNumber() {
        MonthlyReportService service = new MonthlyReportService(
                mock(MonthlyReportRepository.class),
                mock(MonthlyReportItemRepository.class),
                mock(ReportSectionRepository.class),
                mock(MonthlyReportGenerationRepository.class)
        );
        MonthlyReportGenerateRequest invalid = new MonthlyReportGenerateRequest(
                1L, 9, 8, "山东省工业和信息化厅", "有关单位", "联系人 12345678", "qa-reviewer"
        );

        assertThatThrownBy(() -> service.generateMonthlyReport(invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("总期号");
    }

    private MonthlyReportItem item(Long id, Long sectionId, String title, String content) {
        MonthlyReportItem item = new MonthlyReportItem();
        item.setId(id);
        item.setReportId(1L);
        item.setSectionId(sectionId);
        item.setStatus(ReportItemStatus.CONFIRMED);
        item.setSourceType(ReportSourceType.POLICY);
        item.setItemTitle(title);
        item.setFinalContent(content);
        return item;
    }

    private ReportSection section(Long id, String code) {
        ReportSection section = new ReportSection();
        section.setId(id);
        section.setSectionCode(code);
        return section;
    }
    private MonthlyReport report(MonthlyReportStatus status) {
        MonthlyReport report = new MonthlyReport();
        report.setId(1L);
        report.setReportYear(2026);
        report.setReportMonth(8);
        report.setTitle("2026年8月人工智能政策月报");
        report.setStatus(status);
        return report;
    }

    private MonthlyReportGenerateRequest request() {
        return new MonthlyReportGenerateRequest(
                1L,
                8,
                8,
                "山东省工业和信息化厅",
                "有关单位",
                "联系人 12345678",
                "qa-reviewer"
        );
    }
}