package com.itheima.policydailyagent.service.search;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 纯函数化的轮次规划器。第一轮由调用方直接使用用户关键词 + 用户信源；
 * 后续轮次根据主题覆盖度，从未覆盖/覆盖不足且"尚未执行过的"主题中选取关键词，
 * 关键词扩展来源于可配置主题词典（本阶段主题即关键词，后续可扩展同义词），
 * 从而避免重复执行相同的关键词集合。
 */
@Component
public class SearchRoundPlanner {

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
