package com.itheima.policydailyagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itheima.policydailyagent.domain.analysis.AnalysisRunStatus;
import com.itheima.policydailyagent.domain.analysis.PolicyAnalysis;
import com.itheima.policydailyagent.domain.policy.ContentCompleteness;
import com.itheima.policydailyagent.domain.policy.PolicyAnalysisStatus;
import com.itheima.policydailyagent.domain.policy.PolicyReviewStatus;
import com.itheima.policydailyagent.domain.report.ReportSection;
import com.itheima.policydailyagent.dto.PolicyAnalysisDraft;
import com.itheima.policydailyagent.dto.PolicyAnalysisRequest;
import com.itheima.policydailyagent.dto.PolicyBasicInfoView;
import com.itheima.policydailyagent.entity.PolicyDocument;
import com.itheima.policydailyagent.repository.MonthlyReportItemRepository;
import com.itheima.policydailyagent.repository.PolicyAnalysisRepository;
import com.itheima.policydailyagent.repository.PolicyDocumentRepository;
import com.itheima.policydailyagent.repository.ReportSectionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PolicyAnalysisWorkflowServiceTests {

    private final PolicyDocumentRepository policyRepository = mock(PolicyDocumentRepository.class);
    private final PolicyAnalysisRepository analysisRepository = mock(PolicyAnalysisRepository.class);
    private final ReportSectionRepository sectionRepository = mock(ReportSectionRepository.class);
    private final MonthlyReportItemRepository itemRepository = mock(MonthlyReportItemRepository.class);
    private final PolicySectionRecommendationService recommendationService =
            new PolicySectionRecommendationService();
    private final PolicyAnalysisAgent analysisAgent = mock(PolicyAnalysisAgent.class);
    private final PolicyAttachmentPersistenceService attachmentPersistenceService =
            mock(PolicyAttachmentPersistenceService.class);
    private final PolicyAnalysisWorkflowService service = new PolicyAnalysisWorkflowService(
            policyRepository,
            analysisRepository,
            sectionRepository,
            itemRepository,
            recommendationService,
            analysisAgent,
            attachmentPersistenceService,
            new ObjectMapper()
    );

    @Test
    void shouldRejectPolicyThatWasNotAcceptedByHuman() {
        PolicyDocument policy = policy(PolicyReviewStatus.PENDING);
        when(policyRepository.findById(7L)).thenReturn(Optional.of(policy));

        assertThatThrownBy(() -> service.analyze(7L, new PolicyAnalysisRequest("analyst")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ACCEPTED");

        verifyNoInteractions(analysisAgent);
        verify(analysisRepository, never()).save(any());
    }

    @Test
    void shouldRejectIncompleteContentBeforeCallingAgent() {
        PolicyDocument policy = policy(PolicyReviewStatus.ACCEPTED);
        policy.setContentCompleteness(ContentCompleteness.PAGE_ONLY);
        when(policyRepository.findById(7L)).thenReturn(Optional.of(policy));

        assertThatThrownBy(() -> service.analyze(7L, new PolicyAnalysisRequest("analyst")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PAGE_ONLY")
                .hasMessageContaining("重新抓取");

        verifyNoInteractions(analysisAgent);
        verify(analysisRepository, never()).save(any());
    }

    @Test
    void shouldNotCreateAnalysisWhenAgentIsUnavailable() {
        PolicyDocument policy = policy(PolicyReviewStatus.ACCEPTED);
        when(policyRepository.findById(7L)).thenReturn(Optional.of(policy));
        when(analysisAgent.isAvailable()).thenReturn(false);

        assertThatThrownBy(() -> service.analyze(7L, new PolicyAnalysisRequest("analyst")))
                .isInstanceOf(AgentUnavailableException.class)
                .hasMessageContaining("未启用");

        verify(analysisRepository, never()).save(any());
        assertThat(policy.getAnalysisStatus()).isEqualTo(PolicyAnalysisStatus.READY);
    }

    @Test
    void shouldPersistVersionedAnalysisAndOpenConfirmationGate() {
        PolicyDocument policy = policy(PolicyReviewStatus.ACCEPTED);
        ReportSection section = section();
        when(policyRepository.findById(7L)).thenReturn(Optional.of(policy));
        when(analysisAgent.isAvailable()).thenReturn(true);
        when(analysisAgent.modelName()).thenReturn("deepseek-chat");
        when(sectionRepository.findByActiveTrueOrderBySortOrderAsc()).thenReturn(List.of(section));
        when(analysisRepository.findTopByPolicyIdOrderByVersionNoDesc(7L))
                .thenReturn(Optional.empty());
        when(analysisRepository.save(any(PolicyAnalysis.class))).thenAnswer(invocation -> {
            PolicyAnalysis value = invocation.getArgument(0);
            if (value.getId() == null) {
                value.setId(91L);
            }
            return value;
        });
        when(itemRepository.findByAnalysisIdOrderByCreatedAtDesc(91L)).thenReturn(List.of());
        when(analysisAgent.analyze(eq(policy), eq(List.of(section)), any()))
                .thenReturn(new PolicyAnalysisDraft(
                        new PolicyBasicInfoView(
                                "政策标题",
                                "工业和信息化部",
                                "2026-08-01",
                                "政策背景"
                        ),
                        "核心部署",
                        "人工智能相关内容",
                        "NATIONAL",
                        "国家层面政策",
                        "部署人工智能赋能制造业工作",
                        "2026年8月，工业和信息化部部署人工智能赋能制造业重点工作。",
                        List.of("原文证据")
                ));

        var result = service.analyze(7L, new PolicyAnalysisRequest("analyst"));

        assertThat(result.id()).isEqualTo(91L);
        assertThat(result.versionNo()).isEqualTo(1);
        assertThat(result.runStatus()).isEqualTo(AnalysisRunStatus.SUCCEEDED);
        assertThat(result.recommendedSectionCode()).isEqualTo("NATIONAL");
        assertThat(result.generatedTitle()).contains("人工智能");
        assertThat(policy.getAnalysisStatus()).isEqualTo(PolicyAnalysisStatus.ANALYZED);
        verify(policyRepository, atLeastOnce()).save(policy);
    }

    private PolicyDocument policy(PolicyReviewStatus status) {
        PolicyDocument policy = new PolicyDocument();
        policy.setId(7L);
        policy.setTitle("政策标题");
        policy.setSourceName("工业和信息化部");
        policy.setSourceUrl("https://example.gov.cn/policy");
        policy.setCleanedContent("政策正文，部署人工智能赋能制造业工作。");
        policy.setReviewStatus(status);
        policy.setContentCompleteness(ContentCompleteness.COMPLETE);
        policy.setAnalysisStatus(status == PolicyReviewStatus.ACCEPTED
                ? PolicyAnalysisStatus.READY
                : PolicyAnalysisStatus.NOT_ANALYZED);
        return policy;
    }

    @Test
    void shouldPersistFailedQualityReportAndKeepConfirmationClosed() {
        PolicyDocument policy = policy(PolicyReviewStatus.ACCEPTED);
        when(policyRepository.findById(7L)).thenReturn(Optional.of(policy));
        when(analysisAgent.isAvailable()).thenReturn(true);
        when(sectionRepository.findByActiveTrueOrderBySortOrderAsc()).thenReturn(List.of(section()));
        when(analysisRepository.findTopByPolicyIdOrderByVersionNoDesc(7L)).thenReturn(Optional.empty());
        when(analysisRepository.save(any(PolicyAnalysis.class))).thenAnswer(inv -> {
            PolicyAnalysis value = inv.getArgument(0); value.setId(91L); return value;
        });
        var report = new com.itheima.policydailyagent.dto.DraftQualityReport(false, 2,
                List.of(new com.itheima.policydailyagent.dto.DraftQualityReport.Attempt(3, List.of("原文证据不匹配"))));
        when(analysisAgent.analyze(eq(policy), anyList(), any())).thenThrow(new DraftValidationException(report));
        assertThatThrownBy(() -> service.analyze(7L, new PolicyAnalysisRequest("analyst")))
                .isInstanceOf(PolicyAnalysisExecutionException.class);
        org.mockito.ArgumentCaptor<PolicyAnalysis> saved = org.mockito.ArgumentCaptor.forClass(PolicyAnalysis.class);
        verify(analysisRepository, atLeastOnce()).save(saved.capture());
        PolicyAnalysis failed = saved.getValue();
        assertThat(failed.getRunStatus()).isEqualTo(AnalysisRunStatus.FAILED);
        assertThat(failed.getQualityReportJson()).contains("原文证据不匹配", "\"passed\":false");
        assertThatThrownBy(() -> {
            when(analysisRepository.findById(91L)).thenReturn(Optional.of(failed));
            service.requireSucceededAnalysis(7L, 91L);
        }).hasMessageContaining("SUCCEEDED");
        verifyNoInteractions(itemRepository);
    }

    private ReportSection section() {
        ReportSection section = new ReportSection();
        section.setId(11L);
        section.setSectionCode("NATIONAL");
        section.setSectionName("（一）国家重点事项");
        section.setSortOrder(110);
        section.setWritingGuide("按照时间、主体、核心内容和重点任务组织。");
        section.setContentPlaceholder("{{SECTION_NATIONAL}}");
        section.setActive(true);
        return section;
    }
}
