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
}
