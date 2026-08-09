package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.entity.DailyTask;
import com.itheima.policydailyagent.entity.PolicyDocument;
import com.itheima.policydailyagent.repository.DailyTaskRepository;
import com.itheima.policydailyagent.repository.PolicyDocumentRepository;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyReportServiceTests {

    private final PolicyDocumentRepository policyDocumentRepository = mock(PolicyDocumentRepository.class);
    private final DailyTaskRepository dailyTaskRepository = mock(DailyTaskRepository.class);
    private final DailyReportService dailyReportService = new DailyReportService(
            policyDocumentRepository,
            dailyTaskRepository
    );

    @Test
    void shouldWriteApprovedPolicyEvidenceWhenSummaryIsEmpty() throws Exception {
        DailyTask task = new DailyTask();
        task.setTaskName("人工智能政策日报采集");
        task.setTargetStartDate(LocalDate.of(2026, 8, 8));
        task.setTargetEndDate(LocalDate.of(2026, 8, 8));

        PolicyDocument policy = new PolicyDocument();
        policy.setTitle("工业和信息化部部署人工智能赋能新型工业化工作");
        policy.setSourceName("工业和信息化部");
        policy.setPublishDate(LocalDate.of(2026, 8, 8));
        policy.setSourceUrl("https://www.miit.gov.cn/example.html");
        policy.setCategory("国家重点事项");
        policy.setAuthorityLevel("NATIONAL_AUTHORITY");
        policy.setEvidenceSnippet("会议提出加快人工智能与制造业深度融合，推进重点行业场景落地。");
        policy.setSummary("");

        when(dailyTaskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(policyDocumentRepository.findByDailyTaskIdAndReviewStatusOrderByPublishDateDescCreatedAtDesc(
                1L,
                PolicyReviewService.APPROVED
        )).thenReturn(List.of(policy));

        byte[] report = dailyReportService.generateDailyReport(1L);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(report))) {
            String text = document.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText())
                    .collect(Collectors.joining("\n"));

            assertThat(text)
                    .contains("政策日报")
                    .contains("审核通过：1条")
                    .contains("工业和信息化部部署人工智能赋能新型工业化工作")
                    .contains("会议提出加快人工智能与制造业深度融合")
                    .contains("https://www.miit.gov.cn/example.html")
                    .doesNotContain("【栏目定位】")
                    .doesNotContain("{{SECTION_");
        }

        verify(policyDocumentRepository)
                .findByDailyTaskIdAndReviewStatusOrderByPublishDateDescCreatedAtDesc(
                        1L,
                        PolicyReviewService.APPROVED
                );
    }

    @Test
    void shouldRejectReportWhenTaskHasNoApprovedPolicies() {
        DailyTask task = new DailyTask();
        task.setTaskName("空任务");
        when(dailyTaskRepository.findById(2L)).thenReturn(Optional.of(task));
        when(policyDocumentRepository.findByDailyTaskIdAndReviewStatusOrderByPublishDateDescCreatedAtDesc(
                2L,
                PolicyReviewService.APPROVED
        )).thenReturn(List.of());

        assertThatThrownBy(() -> dailyReportService.generateDailyReport(2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No approved policy documents");
    }

    @Test
    void shouldRejectApprovedPolicyOutsideTaskDateRange() {
        DailyTask task = new DailyTask();
        task.setTaskName("当日日报");
        task.setTargetEndDate(LocalDate.of(2026, 8, 9));

        PolicyDocument historicalPolicy = new PolicyDocument();
        historicalPolicy.setTitle("历史政策");
        historicalPolicy.setPublishDate(LocalDate.of(2025, 12, 24));
        historicalPolicy.setSourceUrl("https://example.gov.cn/history.html");
        historicalPolicy.setEvidenceSnippet("历史政策内容");

        when(dailyTaskRepository.findById(3L)).thenReturn(Optional.of(task));
        when(policyDocumentRepository.findByDailyTaskIdAndReviewStatusOrderByPublishDateDescCreatedAtDesc(
                3L,
                PolicyReviewService.APPROVED
        )).thenReturn(List.of(historicalPolicy));

        assertThatThrownBy(() -> dailyReportService.generateDailyReport(3L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("task date range");
    }}
