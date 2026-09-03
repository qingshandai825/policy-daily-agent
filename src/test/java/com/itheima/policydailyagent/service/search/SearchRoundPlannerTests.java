package com.itheima.policydailyagent.service.search;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SearchRoundPlannerTests {

    private final SearchRoundPlanner planner = new SearchRoundPlanner();

    private static final List<String> TOPICS = List.of("人工智能", "大模型", "算力");
    private static final List<String> SOURCES = List.of("gov", "miit");

    private static TopicCoverageResult coverage(String topic, CoverageStatus status) {
        return new TopicCoverageResult(topic, 0, status, List.of());
    }

    @Test
    void returnsEmptyWhenTopicsEmpty() {
        assertThat(planner.planNextRound(List.of(), List.of(), List.of(), SOURCES, 5)).isEmpty();
    }

    @Test
    void returnsEmptyWhenSourceIdsNull() {
        assertThat(planner.planNextRound(List.of(), List.of(), TOPICS, null, 5)).isEmpty();
    }

    @Test
    void picksUncoveredTopicKeywordsExcludingExecuted() {
        List<TopicCoverageResult> coverage = List.of(
                coverage("人工智能", CoverageStatus.COVERED),
                coverage("大模型", CoverageStatus.NOT_COVERED),
                coverage("算力", CoverageStatus.INSUFFICIENT)
        );
        List<String> executed = List.of("人工智能");

        Optional<RoundPlan> plan = planner.planNextRound(coverage, executed, TOPICS, SOURCES, 5);

        assertThat(plan).isPresent();
        assertThat(plan.get().keywords()).containsExactly("大模型", "算力");
        assertThat(plan.get().sourceIds()).isEqualTo(SOURCES);
    }

    @Test
    void limitsKeywordsToMaxPerRound() {
        List<TopicCoverageResult> coverage = List.of(
                coverage("人工智能", CoverageStatus.NOT_COVERED),
                coverage("大模型", CoverageStatus.NOT_COVERED),
                coverage("算力", CoverageStatus.NOT_COVERED)
        );

        Optional<RoundPlan> plan = planner.planNextRound(coverage, List.of(), TOPICS, SOURCES, 2);

        assertThat(plan).isPresent();
        assertThat(plan.get().keywords()).hasSize(2);
    }

    @Test
    void excludesTopicsOutsideDictionary() {
        List<TopicCoverageResult> coverage = List.of(
                coverage("人工智能", CoverageStatus.NOT_COVERED),
                coverage("不在词典内", CoverageStatus.NOT_COVERED)
        );

        Optional<RoundPlan> plan = planner.planNextRound(coverage, List.of(), TOPICS, SOURCES, 5);

        assertThat(plan).isPresent();
        assertThat(plan.get().keywords()).containsExactly("人工智能");
    }

    @Test
    void returnsEmptyWhenAllTopicsCovered() {
        List<TopicCoverageResult> coverage = List.of(
                coverage("人工智能", CoverageStatus.COVERED),
                coverage("大模型", CoverageStatus.COVERED),
                coverage("算力", CoverageStatus.COVERED)
        );

        assertThat(planner.planNextRound(coverage, List.of(), TOPICS, SOURCES, 5)).isEmpty();
    }
}
