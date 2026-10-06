package com.itheima.policydailyagent.service.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itheima.policydailyagent.service.memory.SearchTaskMemoryContext;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class FeedbackPlannerTests {
    private final SearchRoundPlanner planner = new SearchRoundPlanner();
    private final SearchFeedback feedback = new SearchFeedback("SEMANTIC", "test",
            List.of(new TopicCoverageResult("人工智能", 1, CoverageStatus.INSUFFICIENT, List.of(7L))),
            List.of(new SearchFeedback.TermEvidence("人工智能", "行业大模型", 7L, "推广行业大模型")), "材料不足");

    @Test
    void selectsGroundedTermsAndOnlyUnexecutedQuerySourcePairs() {
        var history = List.of(new SearchTaskMemoryContext.ExecutedPlan(List.of("行业大模型", "人工智能", "智能工厂"), List.of("gov")));
        var plan = planner.planFromFeedback(feedback, history, List.of("人工智能"), List.of("gov", "province"), 1).orElseThrow();
        assertThat(plan.keywords()).containsExactly("行业大模型");
        assertThat(plan.sourceIds()).containsExactly("province");
    }

    @Test
    void noEvidenceStillAllowsBoundedDictionaryExpansion() {
        var history = List.of(new SearchTaskMemoryContext.ExecutedPlan(List.of("人工智能"), List.of("gov")));
        var noEvidence = new SearchFeedback("SEMANTIC", "test",
                List.of(new TopicCoverageResult("人工智能", 0, CoverageStatus.NOT_COVERED, List.of())), List.of(), "无材料");
        var plan = planner.planFromFeedback(noEvidence, history, List.of("人工智能"), List.of("gov"), 2).orElseThrow();
        assertThat(plan.keywords()).containsExactly("智能工厂", "行业大模型");
    }

    @Test
    void snapshotPersistsSemanticModeAndVersionOneStaysRuleBased() throws Exception {
        var mapper = new ObjectMapper();
        var config = new MultiRoundConfig(true, 3, 2, 1, 5, 2000, List.of("人工智能"), true);
        var current = mapper.readValue(mapper.writeValueAsString(SearchRunParams.of(5, true, true, config)), SearchRunParams.class);
        assertThat(current.toConfig().semanticFeedbackEnabled()).isTrue();
        assertThat(current.toConfig().materialSufficiencyEnabled()).isTrue();
        var old = mapper.readValue("""
                {"version":1,"maxLinksPerSource":5,"filterByKeyword":true,"multiRoundEnabled":true,
                 "maxRounds":3,"noGrowthRounds":2,"coverageThreshold":1,"maxKeywordsPerRound":5,
                 "maxContentScanLength":2000,"topics":["人工智能"]}
                """, SearchRunParams.class);
        assertThat(old.toConfig().semanticFeedbackEnabled()).isFalse();
        assertThat(old.toConfig().materialSufficiencyEnabled()).isFalse();
        var versionTwo = mapper.readValue(mapper.writeValueAsString(current).replace("\"version\":3", "\"version\":2"), SearchRunParams.class);
        assertThat(versionTwo.toConfig().semanticFeedbackEnabled()).isTrue();
        assertThat(versionTwo.toConfig().materialSufficiencyEnabled()).isFalse();
    }

    @Test
    void modelGapCanTriggerSearchEvenWhenTopicStatisticsAreCovered() {
        var covered = new SearchFeedback("SEMANTIC", "test",
                List.of(new TopicCoverageResult("人工智能", 1, CoverageStatus.COVERED, List.of(7L))), feedback.terms(), "",
                new SearchFeedback.MaterialAssessment(false, List.of("人工智能"), List.of(7L), "只有背景描述，缺少实质举措", List.of()));
        var history = List.of(new SearchTaskMemoryContext.ExecutedPlan(List.of("人工智能"), List.of("gov")));
        assertThat(planner.planFromFeedback(covered, history, List.of("人工智能"), List.of("gov"), 2, true)
                .orElseThrow().keywords()).contains("行业大模型");
    }
}
