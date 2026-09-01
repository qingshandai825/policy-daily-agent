package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.domain.analysis.AnalysisRunStatus;
import com.itheima.policydailyagent.domain.analysis.PolicyAnalysis;
import com.itheima.policydailyagent.domain.policy.PolicyReviewStatus;
import com.itheima.policydailyagent.domain.report.*;
import com.itheima.policydailyagent.dto.PolicyAnalysisConfirmRequest;
import com.itheima.policydailyagent.entity.PolicyDocument;
import com.itheima.policydailyagent.repository.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReportContentPoolServiceTests {

    private final PolicyDocumentRepository policyRepository = mock(PolicyDocumentRepository.class);
    private final PolicyAnalysisWorkflowService workflowService = mock(PolicyAnalysisWorkflowService.class);
    private final MonthlyReportRepository reportRepository = mock(MonthlyReportRepository.class);
    private final ReportSectionRepository sectionRepository = mock(ReportSectionRepository.class);
    private final MonthlyReportItemRepository itemRepository = mock(MonthlyReportItemRepository.class);
    private final MonthlyReportItemRevisionRepository revisionRepository =
            mock(MonthlyReportItemRevisionRepository.class);
    private final MonthlyReportItemSourceRepository sourceRepository =
            mock(MonthlyReportItemSourceRepository.class);
    private final ReportContentPoolService service = new ReportContentPoolService(
            policyRepository,
            workflowService,
            reportRepository,
            sectionRepository,
            itemRepository,
            revisionRepository,
            sourceRepository
    );

    @Test
    void shouldCreateConfirmedItemRevisionAndTraceableSourceSnapshot() {
        PolicyDocument policy = acceptedPolicy();
        PolicyAnalysis analysis = successfulAnalysis();
        MonthlyReport report = report();
        ReportSection section = section();

        when(policyRepository.findById(7L)).thenReturn(Optional.of(policy));
        when(workflowService.requireSucceededAnalysis(7L, 91L)).thenReturn(analysis);
        when(sectionRepository.findBySectionCode("NATIONAL")).thenReturn(Optional.of(section));
        when(reportRepository.findByReportYearAndReportMonth(2026, 8)).thenReturn(Optional.of(report));
        when(itemRepository.existsByReportIdAndPolicyId(31L, 7L)).thenReturn(false);
        when(itemRepository.findTopByReportIdAndSectionIdOrderBySortOrderDesc(31L, 11L))
                .thenReturn(Optional.empty());
        when(itemRepository.save(any(MonthlyReportItem.class))).thenAnswer(invocation -> {
            MonthlyReportItem item = invocation.getArgument(0);
            item.setId(101L);
            return item;
        });

        var result = service.confirm(7L, 91L, request());

        assertThat(result.reportId()).isEqualTo(31L);
        assertThat(result.reportItemId()).isEqualTo(101L);
        assertThat(result.status()).isEqualTo(ReportItemStatus.CONFIRMED);

        ArgumentCaptor<MonthlyReportItem> item = ArgumentCaptor.forClass(MonthlyReportItem.class);
        verify(itemRepository).save(item.capture());
        assertThat(item.getValue().getPolicyId()).isEqualTo(7L);
        assertThat(item.getValue().getAnalysisId()).isEqualTo(91L);
        assertThat(item.getValue().getAgentDraft()).isEqualTo("Agent 原始月报正文");
        assertThat(item.getValue().getFinalContent()).isEqualTo("人工确认后的月报正文");

        ArgumentCaptor<MonthlyReportItemRevision> revision =
                ArgumentCaptor.forClass(MonthlyReportItemRevision.class);
        verify(revisionRepository).save(revision.capture());
        assertThat(revision.getValue().getRevisionNo()).isEqualTo(1);
        assertThat(revision.getValue().getChangeType()).isEqualTo("ANALYSIS_CONFIRMED");

        ArgumentCaptor<MonthlyReportItemSource> source =
                ArgumentCaptor.forClass(MonthlyReportItemSource.class);
        verify(sourceRepository).save(source.capture());
        assertThat(source.getValue().getSourceUrl()).isEqualTo(policy.getSourceUrl());
        assertThat(source.getValue().getSourceContentSnapshot()).isEqualTo(policy.getCleanedContent());
    }

    @Test
    void shouldRejectDuplicatePolicyInSameMonthlyReport() {
        PolicyDocument policy = acceptedPolicy();
        when(policyRepository.findById(7L)).thenReturn(Optional.of(policy));
        when(workflowService.requireSucceededAnalysis(7L, 91L)).thenReturn(successfulAnalysis());
        when(sectionRepository.findBySectionCode("NATIONAL")).thenReturn(Optional.of(section()));
        when(reportRepository.findByReportYearAndReportMonth(2026, 8)).thenReturn(Optional.of(report()));
        when(itemRepository.existsByReportIdAndPolicyId(31L, 7L)).thenReturn(true);

        assertThatThrownBy(() -> service.confirm(7L, 91L, request()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已经进入");

        verify(itemRepository, never()).save(any());
        verifyNoInteractions(revisionRepository, sourceRepository);
    }

    private PolicyAnalysisConfirmRequest request() {
        return new PolicyAnalysisConfirmRequest(
                2026,
                8,
                "NATIONAL",
                "人工智能赋能制造业重点部署",
                "人工确认后的月报正文",
                "reviewer"
        );
    }

    private PolicyDocument acceptedPolicy() {
        PolicyDocument policy = new PolicyDocument();
        policy.setId(7L);
        policy.setTitle("政策标题");
        policy.setSourceUrl("https://example.gov.cn/policy");
        policy.setCleanedContent("完整的清洗后政策正文");
        policy.setReviewStatus(PolicyReviewStatus.ACCEPTED);
        return policy;
    }

    private PolicyAnalysis successfulAnalysis() {
        PolicyAnalysis analysis = new PolicyAnalysis();
        analysis.setId(91L);
        analysis.setPolicyId(7L);
        analysis.setRunStatus(AnalysisRunStatus.SUCCEEDED);
        analysis.setGeneratedTitle("Agent 标题");
        analysis.setGeneratedContent("Agent 原始月报正文");
        return analysis;
    }

    private MonthlyReport report() {
        MonthlyReport report = new MonthlyReport();
        report.setId(31L);
        report.setReportYear(2026);
        report.setReportMonth(8);
        report.setTitle("2026年8月人工智能赋能制造业工作月报");
        return report;
    }

    private ReportSection section() {
        ReportSection section = new ReportSection();
        section.setId(11L);
        section.setSectionCode("NATIONAL");
        section.setActive(true);
        section.setContentPlaceholder("{{SECTION_NATIONAL}}");
        return section;
    }
}
