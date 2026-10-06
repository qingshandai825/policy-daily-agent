package com.itheima.policydailyagent.service.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchStopPolicyTests {

    private final SearchStopPolicy policy = new SearchStopPolicy();

    private static TopicCoverageResult covered(String topic) {
        return new TopicCoverageResult(topic, 1, CoverageStatus.COVERED, List.of(1L));
    }

    @Test
    void stopsWhenAllTopicsCovered() {
        StopDecision decision = policy.decide(1, 3, List.of(covered("人工智能")), 2, 0, true);
        assertThat(decision.shouldStop()).isTrue();
        assertThat(decision.reason()).isEqualTo("ALL_TOPICS_COVERED");
    }

    @Test
    void stopsWhenMaxRoundsReached() {
        List<TopicCoverageResult> coverage = List.of(
                new TopicCoverageResult("人工智能", 0, CoverageStatus.NOT_COVERED, List.of()));
        StopDecision decision = policy.decide(3, 3, coverage, 2, 0, true);
        assertThat(decision.shouldStop()).isTrue();
        assertThat(decision.reason()).isEqualTo("MAX_ROUNDS_REACHED");
    }

    @Test
    void stopsWhenConsecutiveNoGrowthReachesThreshold() {
        List<TopicCoverageResult> coverage = List.of(
                new TopicCoverageResult("人工智能", 0, CoverageStatus.NOT_COVERED, List.of()));
        StopDecision decision = policy.decide(2, 3, coverage, 2, 2, true);
        assertThat(decision.shouldStop()).isTrue();
        assertThat(decision.reason()).isEqualTo("NO_GROWTH");
    }

    @Test
    void stopsWhenNoNextPlan() {
        List<TopicCoverageResult> coverage = List.of(
                new TopicCoverageResult("人工智能", 0, CoverageStatus.NOT_COVERED, List.of()));
        StopDecision decision = policy.decide(1, 3, coverage, 2, 0, false);
        assertThat(decision.shouldStop()).isTrue();
        assertThat(decision.reason()).isEqualTo("NO_NEW_PLAN");
    }

    @Test
    void continuesWhenNoConditionMet() {
        List<TopicCoverageResult> coverage = List.of(
                new TopicCoverageResult("人工智能", 0, CoverageStatus.NOT_COVERED, List.of()));
        StopDecision decision = policy.decide(1, 3, coverage, 2, 0, true);
        assertThat(decision.shouldStop()).isFalse();
        assertThat(decision.reason()).isNull();
    }

    @Test
    void emptyCoverageDoesNotCountAsAllCovered() {
        StopDecision decision = policy.decide(1, 3, List.of(), 2, 0, true);
        assertThat(decision.shouldStop()).isFalse();
    }

    @Test
    void materialAssessmentControlsSemanticCompletionAndBudgetStillStopsContinuing() {
        var insufficient = new SearchFeedback("SEMANTIC", "test", List.of(covered("人工智能")), List.of(), "",
                new SearchFeedback.MaterialAssessment(false, List.of("人工智能"), List.of(1L), "缺少具体部署", List.of()));
        assertThat(policy.checkBeforeExecution(2, 3, insufficient.coverage(), 2, 0, insufficient, true)).isEmpty();
        assertThat(policy.checkBeforeExecution(4, 3, insufficient.coverage(), 2, 0, insufficient, true))
                .contains("MAX_ROUNDS_REACHED");
        assertThat(policy.checkBeforeExecution(2, 3, insufficient.coverage(), 2, 2, insufficient, true))
                .contains("NO_GROWTH");
        assertThat(policy.checkBeforeExecution(2, 3, insufficient.coverage(), 2, 0, insufficient, false))
                .contains("ALL_TOPICS_COVERED");
        var sufficient = new SearchFeedback("SEMANTIC", "test", List.of(), List.of(), "",
                new SearchFeedback.MaterialAssessment(true, List.of(), List.of(1L), "原文足以支持目标", List.of()));
        assertThat(policy.checkBeforeExecution(2, 3, List.of(), 2, 0, sufficient, true))
                .contains("LLM_MATERIAL_SUFFICIENT");
    }

    @Test
    void invalidOrFallbackAssessmentCannotStopAsSufficient() {
        var invalid = new SearchFeedback("SEMANTIC", "test", List.of(covered("人工智能")), List.of(), "",
                new SearchFeedback.MaterialAssessment(true, List.of(), List.of(), "已够", List.of("没有有效证据")));
        assertThat(policy.checkBeforeExecution(2, 3, invalid.coverage(), 2, 0, invalid, true)).isEmpty();
        var fallback = new SearchFeedback("RULE_FALLBACK", null, invalid.coverage(), List.of(), "关键词已覆盖");
        assertThat(policy.checkBeforeExecution(2, 3, fallback.coverage(), 2, 0, fallback, true)).isEmpty();
    }
}
