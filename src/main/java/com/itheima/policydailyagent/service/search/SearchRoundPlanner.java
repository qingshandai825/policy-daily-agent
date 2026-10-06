package com.itheima.policydailyagent.service.search;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import com.itheima.policydailyagent.service.memory.SearchTaskMemoryContext;

/**
 * 纯函数化的轮次规划器。第一轮由调用方直接使用用户关键词 + 用户信源；
 * 规则模式根据覆盖统计规划；语义模式根据材料缺口、原文术语和主题词典规划。
 * 已执行计划参与关键词与信源配对检查，避免重复执行相同查询。
 */
@Component
public class SearchRoundPlanner {

    /**
     * 程序按缺口顺序、原文术语、主题词典选择未执行的 query/source 配对。
     * 相同关键词允许在不同信源执行；Memory 里的配对历史参与规划，而非仅用于展示。
     */
    public Optional<RoundPlan> planFromFeedback(SearchFeedback feedback,
            List<SearchTaskMemoryContext.ExecutedPlan> history, List<String> topics,
            List<String> sourceIds, int maxKeywordsPerRound) {
        return planFromFeedback(feedback, history, topics, sourceIds, maxKeywordsPerRound, false);
    }

    public Optional<RoundPlan> planFromFeedback(SearchFeedback feedback,
            List<SearchTaskMemoryContext.ExecutedPlan> history, List<String> topics,
            List<String> sourceIds, int maxKeywordsPerRound, boolean materialSufficiencyEnabled) {
        if (feedback == null || sourceIds == null || topics == null) return Optional.empty();
        if (materialSufficiencyEnabled && feedback.canFinishByAssessment()) return Optional.empty();
        var assessment = feedback.materialAssessment();
        List<String> focusTopics = materialSufficiencyEnabled && assessment != null
                && assessment.validationIssues().isEmpty() && Boolean.FALSE.equals(assessment.materialSufficient())
                ? assessment.missingTopics() : topics;
        Set<String> executedPairs = new HashSet<>();
        for (var plan : history == null ? List.<SearchTaskMemoryContext.ExecutedPlan>of() : history) {
            for (String keyword : plan.keywords()) for (String source : plan.sources()) {
                executedPairs.add(pair(keyword, source));
            }
        }
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (String topic : focusTopics) {
            boolean covered = feedback.coverage().stream()
                    .anyMatch(c -> topic.equals(c.topic()) && c.status() == CoverageStatus.COVERED);
            if (covered && !materialSufficiencyEnabled) continue;
            feedback.terms().stream().filter(t -> topic.equals(t.topic()))
                    .map(SearchFeedback.TermEvidence::term).forEach(terms::add);
            terms.addAll(SearchTopicDictionary.searchTerms(topic));
        }
        for (String source : sourceIds) {
            List<String> keywords = new ArrayList<>();
            Set<String> normalized = new HashSet<>();
            for (String term : terms) {
                if (!executedPairs.contains(pair(term, source)) && normalized.add(normalize(term))) {
                    keywords.add(term);
                    if (keywords.size() >= maxKeywordsPerRound) break;
                }
            }
            if (!keywords.isEmpty()) return Optional.of(new RoundPlan(keywords, List.of(source)));
        }
        return Optional.empty();
    }

    private String pair(String keyword, String source) { return normalize(keyword) + "\u0000" + source; }
    private String normalize(String keyword) { return keyword.trim().toLowerCase(Locale.ROOT); }

    public Optional<RoundPlan> planNextRound(
            List<TopicCoverageResult> coverage,
            List<String> executedKeywords,
            List<String> topics,
            List<String> sourceIds,
            int maxKeywordsPerRound
    ) {
        if (topics == null || topics.isEmpty() || sourceIds == null) {
            return Optional.empty();
        }
        Set<String> executed = new HashSet<>(executedKeywords == null ? List.of() : executedKeywords);
        List<String> keywords = (coverage == null ? List.<TopicCoverageResult>of() : coverage).stream()
                .filter(result -> result.status() != CoverageStatus.COVERED)
                .map(TopicCoverageResult::topic)
                .filter(topic -> topic != null && !topic.trim().isEmpty())
                .filter(topic -> !executed.contains(topic))
                .filter(topics::contains)
                .distinct()
                .limit(maxKeywordsPerRound)
                .toList();
        if (keywords.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new RoundPlan(keywords, sourceIds));
    }
}
