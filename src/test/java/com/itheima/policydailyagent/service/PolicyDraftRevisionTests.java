package com.itheima.policydailyagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itheima.policydailyagent.domain.report.ReportSection;
import com.itheima.policydailyagent.dto.PolicyAnalysisDraft;
import com.itheima.policydailyagent.entity.PolicyDocument;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ChatModel;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PolicyDraftRevisionTests {
    private final AgentAvailabilityService availability = mock(AgentAvailabilityService.class);
    private final ChatModel model = mock(ChatModel.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final PolicyAnalysisAgent agent = new PolicyAnalysisAgent(availability, mapper);
    private final PolicyDocument policy = policy();
    private final ReportSection section = section();
    private final PolicySectionRecommendationService.SectionRecommendation rule =
            new PolicySectionRecommendationService.SectionRecommendation("NATIONAL", "程序规则栏目");

    @Test
    void revisesUnsupportedNumberAndEvidenceThenKeepsRuleSection() throws Exception {
        when(availability.requireChatModel()).thenReturn(model);
        when(model.call(anyString())).thenReturn(json("计划建设99个智能工厂。", "建设99个智能工厂"),
                json("计划建设10个智能工厂。", "计划建设10个智能工厂"));
        PolicyAnalysisDraft result = agent.analyze(policy, List.of(section, provincialSection()), rule);
        assertThat(result.qualityReport().passed()).isTrue();
        assertThat(result.qualityReport().revisionCount()).isEqualTo(1);
        assertThat(result.qualityReport().attempts().get(0).issues()).anyMatch(i -> i.contains("99"));
        assertThat(result.recommendedSectionCode()).isEqualTo("NATIONAL");
        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        verify(model, times(2)).call(prompts.capture());
        assertThat(prompts.getAllValues().get(1)).contains("程序检查反馈", "99", "第1条证据");
    }

    @Test
    void exhaustsRevisionBudgetAndReportsFailure() throws Exception {
        when(availability.requireChatModel()).thenReturn(model);
        when(model.call(anyString())).thenReturn(json("已建成99个智能工厂。", "虚构的原文证据"));
        assertThatThrownBy(() -> agent.analyze(policy, List.of(section), rule))
                .isInstanceOf(DraftValidationException.class).hasMessageContaining("限定修订次数");
        verify(model, times(3)).call(anyString());
    }

    @Test
    void repairsMalformedJsonWithinSameBudget() throws Exception {
        when(availability.requireChatModel()).thenReturn(model);
        when(model.call(anyString())).thenReturn("not json", json("计划建设10个智能工厂。", "计划建设10个智能工厂"));
        assertThat(agent.analyze(policy, List.of(section), rule).qualityReport().revisionCount()).isEqualTo(1);
    }

    @Test
    void rejectsCompletionClaimsAndMarkdownEvenWhenNumbersExist() throws Exception {
        PolicyAnalysisDraft draft = mapper.readValue(json("**已建成10个智能工厂。**", "计划建设10个智能工厂"), PolicyAnalysisDraft.class);
        assertThat(new PolicyDraftValidator().check(draft, policy))
                .anyMatch(i -> i.contains("完成状态")).anyMatch(i -> i.contains("Markdown"));
    }

    private String json(String body, String evidence) throws Exception {
        return mapper.writeValueAsString(new PolicyAnalysisDraft(null, "部署智能制造任务", "涉及人工智能", "PROVINCIAL",
                "模型倾向省内", "推进智能制造工作", body, List.of(evidence)));
    }

    @Test
    void rejectsRecombinedDatesAndChangedQuantityUnits() throws Exception {
        policy.setPublishDate(java.time.LocalDate.of(2026, 8, 1));
        var draft = mapper.readValue(json("2026年1月8日，投入10亿元建设智能工厂。", "计划建设10个智能工厂"), PolicyAnalysisDraft.class);
        assertThat(new PolicyDraftValidator().check(draft, policy))
                .anyMatch(i -> i.contains("2026-01-08"))
                .anyMatch(i -> i.contains("10亿元"));
    }
    private static PolicyDocument policy() {
        var policy = new PolicyDocument(); policy.setTitle("智能制造通知");
        policy.setCleanedContent("计划建设10个智能工厂，推进人工智能应用。"); return policy;
    }
    private static ReportSection section() {
        var section = new ReportSection(); section.setSectionCode("NATIONAL"); section.setSectionName("国家事项"); return section;
    }
    private static ReportSection provincialSection() {
        var section = new ReportSection(); section.setSectionCode("PROVINCIAL"); section.setSectionName("省内事项"); return section;
    }
}
