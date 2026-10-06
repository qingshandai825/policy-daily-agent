package com.itheima.policydailyagent.service.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itheima.policydailyagent.service.AgentAvailabilityService;
import com.itheima.policydailyagent.service.memory.SearchTaskMemoryContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ChatModel;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SearchFeedbackServiceTests {
    private final AgentAvailabilityService availability = mock(AgentAvailabilityService.class);
    private final ChatModel model = mock(ChatModel.class);
    private final SearchFeedbackService service = new SearchFeedbackService(availability, new ObjectMapper(), new TopicCoverageEvaluator());
    private final MultiRoundConfig config = new MultiRoundConfig(true, 3, 2, 1, 5, 2000, List.of("人工智能", "算力"), true);
    private final List<CandidateText> candidates = List.of(new CandidateText(7L, "智能制造通知", "", "", "推广行业大模型，建设智能工厂。"));

    @Test
    void validatesEvidenceAndTermsBeforeComputingCoverage() {
        when(availability.isAvailable()).thenReturn(true);
        when(availability.requireChatModel()).thenReturn(model);
        when(model.call(anyString())).thenReturn("""
                {"evidence":[
                  {"topic":"人工智能","policyId":7,"quote":"推广行业大模型"},
                  {"topic":"算力","policyId":999,"quote":"算力基础设施建设"},
                  {"topic":"算力","policyId":7,"quote":"原文不存在的算力部署"}],
                 "terms":[
                  {"topic":"人工智能","term":"行业大模型","policyId":7,"quote":"推广行业大模型"},
                  {"topic":"人工智能","term":"虚构检索词","policyId":7,"quote":"推广行业大模型"}]}
                """);
        var history = List.of(new SearchTaskMemoryContext.ExecutedPlan(List.of("人工智能"), List.of("gov")));
        SearchFeedback result = service.evaluate(candidates, config, history);
        assertThat(result.mode()).isEqualTo("SEMANTIC");
        assertThat(result.coverage().get(0).status()).isEqualTo(CoverageStatus.COVERED);
        assertThat(result.coverage().get(1).status()).isEqualTo(CoverageStatus.NOT_COVERED);
        assertThat(result.terms()).extracting(SearchFeedback.TermEvidence::term).containsExactly("行业大模型");
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(model).call(prompt.capture());
        assertThat(prompt.getValue()).contains("executedPlans", "人工智能", "gov", "不负责规划行动");
    }

    @Test
    void malformedResponseFallsBackWithoutAdoptingTerms() {
        when(availability.isAvailable()).thenReturn(true);
        when(availability.requireChatModel()).thenReturn(model);
        when(model.call(anyString())).thenReturn("模型返回的不是JSON");
        assertThat(service.evaluate(candidates, config, List.of()).mode()).isEqualTo("RULE_FALLBACK");
        verify(model, times(1)).call(anyString());
    }

    @Test
    void disabledModelAndEmptyMaterialsDoNotCallModel() {
        assertThat(service.evaluate(candidates, config, List.of()).mode()).isEqualTo("RULE_FALLBACK");
        when(availability.isAvailable()).thenReturn(true);
        assertThat(service.evaluate(List.of(), config, List.of()).coverage())
                .allMatch(c -> c.status() == CoverageStatus.NOT_COVERED);
        verifyNoInteractions(model);
    }

    @Test
    void failureDetailsDoNotLeakIntoSavedFeedback() {
        when(availability.isAvailable()).thenReturn(true);
        when(availability.requireChatModel()).thenReturn(model);
        when(model.call(anyString())).thenThrow(new IllegalStateException("secret-api-key"));
        assertThat(service.evaluate(candidates, config, List.of()).note()).doesNotContain("secret-api-key");
    }

    @Test
    void acceptsGroundedSufficiencyEvenBelowStatisticalThreshold() {
        when(availability.isAvailable()).thenReturn(true);
        when(availability.requireChatModel()).thenReturn(model);
        when(model.call(anyString())).thenReturn("""
                {"evidence":[{"topic":"人工智能","policyId":7,"quote":"推广行业大模型"},
                             {"topic":"算力","policyId":7,"quote":"建设智算中心"}],
                 "terms":[],"materialSufficient":true,"missingTopics":[],"evidencePolicyIds":[7],
                 "reason":"原文已提供两个目标主题的实质部署"}
                """);
        var highThreshold = new MultiRoundConfig(true, 3, 2, 5, 5, 2000, config.topics(), true);
        var result = service.evaluate(List.of(new CandidateText(7L, "政策部署", "", "", "推广行业大模型，建设智算中心。")), highThreshold, List.of());
        assertThat(result.coverage()).allMatch(c -> c.status() == CoverageStatus.INSUFFICIENT);
        assertThat(result.canFinishByAssessment()).isTrue();
    }

    @Test
    void rejectsSufficiencyWithUnknownReferencesMissingEvidenceOrContradictoryGaps() {
        when(availability.isAvailable()).thenReturn(true);
        when(availability.requireChatModel()).thenReturn(model);
        when(model.call(anyString())).thenReturn("""
                {"evidence":[{"topic":"人工智能","policyId":7,"quote":"推广行业大模型"}],
                 "terms":[],"materialSufficient":true,"missingTopics":["算力"],"evidencePolicyIds":[7,999],
                 "reason":"材料足够"}
                """);
        var result = service.evaluate(candidates, config, List.of());
        assertThat(result.materialAssessment().materialSufficient()).isTrue();
        assertThat(result.canFinishByAssessment()).isFalse();
        assertThat(result.materialAssessment().validationIssues())
                .anyMatch(s -> s.contains("未经引文校验"))
                .anyMatch(s -> s.contains("仍列有材料缺口"))
                .anyMatch(s -> s.contains("每个目标主题"));
    }

    @Test
    void absentAssessmentCannotBeInferredFromCoveredKeywords() {
        when(availability.isAvailable()).thenReturn(true);
        when(availability.requireChatModel()).thenReturn(model);
        when(model.call(anyString())).thenReturn("""
                {"evidence":[{"topic":"人工智能","policyId":7,"quote":"推广行业大模型"}],"terms":[]}
                """);
        var onlyAi = new MultiRoundConfig(true, 3, 2, 1, 5, 2000, List.of("人工智能"), true);
        var result = service.evaluate(candidates, onlyAi, List.of());
        assertThat(result.coverage().get(0).status()).isEqualTo(CoverageStatus.COVERED);
        assertThat(result.canFinishByAssessment()).isFalse();
        assertThat(result.materialAssessment().validationIssues()).contains("缺少materialSufficient判断");
    }
}
