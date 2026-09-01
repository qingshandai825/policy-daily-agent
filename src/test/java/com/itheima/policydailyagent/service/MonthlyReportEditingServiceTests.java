package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.domain.report.MonthlyReport;
import com.itheima.policydailyagent.domain.report.MonthlyReportItem;
import com.itheima.policydailyagent.domain.report.MonthlyReportStatus;
import com.itheima.policydailyagent.domain.report.ReportItemStatus;
import com.itheima.policydailyagent.domain.report.ReportSection;
import com.itheima.policydailyagent.domain.report.ReportSourceType;
import com.itheima.policydailyagent.dto.MonthlyReportActionRequest;
import com.itheima.policydailyagent.dto.ReorderReportSectionRequest;
import com.itheima.policydailyagent.dto.UpdateMonthlyReportItemRequest;
import com.itheima.policydailyagent.repository.MonthlyReportItemRepository;
import com.itheima.policydailyagent.repository.MonthlyReportItemRevisionRepository;
import com.itheima.policydailyagent.repository.MonthlyReportRepository;
import com.itheima.policydailyagent.repository.ReportSectionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MonthlyReportEditingServiceTests {

    @Autowired
    private MonthlyReportEditingService service;

    @Autowired
    private MonthlyReportRepository reportRepository;

    @Autowired
    private ReportSectionRepository sectionRepository;

    @Autowired
    private MonthlyReportItemRepository itemRepository;

    @Autowired
    private MonthlyReportItemRevisionRepository revisionRepository;

    @Test
    void shouldRunHumanEditingConfirmationAndReopenWorkflow() {
        MonthlyReport report = reportRepository.saveAndFlush(report(2026, 8));
        ReportSection national = sectionRepository.saveAndFlush(section("NATIONAL_EDIT", "国家重点事项", 10));
        ReportSection provincial = sectionRepository.saveAndFlush(section("PROVINCIAL_EDIT", "省内工作", 20));
        MonthlyReportItem first = itemRepository.saveAndFlush(item(report, national, "标题一", 10));
        MonthlyReportItem second = itemRepository.saveAndFlush(item(report, national, "标题二", 20));

        var edited = service.updateItem(
                report.getId(),
                first.getId(),
                new UpdateMonthlyReportItemRequest(
                        provincial.getSectionCode(),
                        "人工修改后的标题",
                        "人工修改后的正式月报正文",
                        "editor-a",
                        first.getLockVersion()
                )
        );

        assertThat(edited.activeItemCount()).isEqualTo(2);
        assertThat(edited.canConfirm()).isTrue();
        MonthlyReportItem updatedFirst = itemRepository.findById(first.getId()).orElseThrow();
        assertThat(updatedFirst.getSectionId()).isEqualTo(provincial.getId());
        assertThat(updatedFirst.getItemTitle()).isEqualTo("人工修改后的标题");
        var editRevision = revisionRepository
                .findTopByReportItemIdOrderByRevisionNoDesc(first.getId()).orElseThrow();
        assertThat(editRevision.getChangeType()).isEqualTo("EDITED");
        assertThat(editRevision.getSectionId()).isEqualTo(provincial.getId());

        service.updateItem(
                report.getId(),
                first.getId(),
                new UpdateMonthlyReportItemRequest(
                        national.getSectionCode(),
                        updatedFirst.getItemTitle(),
                        updatedFirst.getFinalContent(),
                        "editor-a",
                        updatedFirst.getLockVersion()
                )
        );
        service.reorderSection(
                report.getId(),
                national.getSectionCode(),
                new ReorderReportSectionRequest(List.of(second.getId(), first.getId()), "editor-b")
        );
        List<MonthlyReportItem> reordered = itemRepository
                .findByReportIdAndSectionIdOrderBySortOrderAsc(report.getId(), national.getId());
        assertThat(reordered).extracting(MonthlyReportItem::getId)
                .containsExactly(second.getId(), first.getId());

        var removed = service.removeItem(
                report.getId(),
                second.getId(),
                new MonthlyReportActionRequest("editor-b", "本月暂不采用")
        );
        assertThat(removed.activeItemCount()).isEqualTo(1);
        assertThat(removed.removedItems()).extracting(item -> item.id()).contains(second.getId());

        var restored = service.restoreItem(
                report.getId(),
                second.getId(),
                new MonthlyReportActionRequest("editor-b", "复核后恢复")
        );
        assertThat(restored.activeItemCount()).isEqualTo(2);
        assertThat(restored.removedItems()).isEmpty();

        var confirmed = service.confirmContent(
                report.getId(),
                new MonthlyReportActionRequest("chief-reviewer", "整月内容审核通过")
        );
        assertThat(confirmed.status()).isEqualTo(MonthlyReportStatus.CONTENT_CONFIRMED);
        assertThat(confirmed.contentConfirmedBy()).isEqualTo("chief-reviewer");

        MonthlyReportItem lockedItem = itemRepository.findById(first.getId()).orElseThrow();
        assertThatThrownBy(() -> service.updateItem(
                report.getId(),
                first.getId(),
                new UpdateMonthlyReportItemRequest(
                        national.getSectionCode(), "确认后修改", "不应保存", "editor-c",
                        lockedItem.getLockVersion()
                )
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重新打开编辑");

        var reopened = service.reopen(
                report.getId(),
                new MonthlyReportActionRequest("chief-reviewer", "需要补充政策")
        );
        assertThat(reopened.status()).isEqualTo(MonthlyReportStatus.DRAFT);
        assertThat(reopened.contentConfirmedAt()).isNull();
    }

    @Test
    void shouldRejectEmptyMonthlyReportConfirmation() {
        MonthlyReport report = reportRepository.saveAndFlush(report(2026, 9));
        sectionRepository.saveAndFlush(section("NATIONAL_EMPTY", "国家重点事项", 10));

        assertThatThrownBy(() -> service.confirmContent(
                report.getId(),
                new MonthlyReportActionRequest("reviewer", null)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少需要一条");
    }

    private MonthlyReport report(int year, int month) {
        MonthlyReport report = new MonthlyReport();
        report.setReportYear(year);
        report.setReportMonth(month);
        report.setTitle(year + "年" + month + "月人工智能赋能制造业工作月报");
        report.setStatus(MonthlyReportStatus.DRAFT);
        return report;
    }

    private ReportSection section(String code, String name, int sortOrder) {
        ReportSection section = new ReportSection();
        section.setSectionCode(code);
        section.setSectionName(name);
        section.setSectionLevel(2);
        section.setSortOrder(sortOrder);
        section.setWritingGuide("测试写作提示");
        section.setContentPlaceholder("{{SECTION_" + code + "}}");
        section.setActive(true);
        return section;
    }

    private MonthlyReportItem item(
            MonthlyReport report,
            ReportSection section,
            String title,
            int sortOrder
    ) {
        MonthlyReportItem item = new MonthlyReportItem();
        item.setReportId(report.getId());
        item.setSectionId(section.getId());
        item.setSourceType(ReportSourceType.POLICY);
        item.setItemTitle(title);
        item.setAgentDraft("Agent 初稿：" + title);
        item.setFinalContent("人工确认正文：" + title);
        item.setStatus(ReportItemStatus.CONFIRMED);
        item.setSortOrder(sortOrder);
        item.setCreatedBy("reviewer");
        item.setUpdatedBy("reviewer");
        return item;
    }
}
