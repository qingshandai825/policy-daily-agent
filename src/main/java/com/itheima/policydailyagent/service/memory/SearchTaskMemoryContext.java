package com.itheima.policydailyagent.service.memory;

import java.time.LocalDate;
import java.util.List;
import com.itheima.policydailyagent.service.search.SearchFeedback;

/**
 * 搜索任务 Memory 的结构化上下文快照，是 context_json 的稳定序列化结构。
 * 只保存摘要性数据与业务对象 ID，绝不保存完整政策正文、附件或 Word 文件。
 * 由 {@link SearchTaskMemoryAssembler} 统一构建与序列化，避免各 Service
 * 手工拼接不一致的 JSON。
 *
 * <p>多轮搜索字段（currentRound/completedRounds/executedPlans/topicCoverage/
 * consecutiveNoGrowthRounds/stopReason）为可选增量：旧 context_json 缺少这些
 * 字段时可正常反序列化（引用类型为 null、基本类型为 0），保持向后兼容。
 */
public record SearchTaskMemoryContext(
        String reportMonth,
        LocalDate targetStartDate,
        LocalDate targetEndDate,
        List<String> keywords,
        List<String> selectedSources,
        List<String> executedSources,
        Counts counts,
        List<Long> candidatePolicyIds,
        List<SourceFailure> sourceFailures,
        int currentRound,
        List<Integer> completedRounds,
        List<ExecutedPlan> executedPlans,
        List<TopicCoverage> topicCoverage,
        int consecutiveNoGrowthRounds,
        String stopReason,
        SearchFeedback searchFeedback
) {

    public SearchTaskMemoryContext(String reportMonth, LocalDate targetStartDate, LocalDate targetEndDate,
            List<String> keywords, List<String> selectedSources, List<String> executedSources, Counts counts,
            List<Long> candidatePolicyIds, List<SourceFailure> sourceFailures, int currentRound,
            List<Integer> completedRounds, List<ExecutedPlan> executedPlans, List<TopicCoverage> topicCoverage,
            int consecutiveNoGrowthRounds, String stopReason) {
        this(reportMonth, targetStartDate, targetEndDate, keywords, selectedSources, executedSources, counts,
                candidatePolicyIds, sourceFailures, currentRound, completedRounds, executedPlans, topicCoverage,
                consecutiveNoGrowthRounds, stopReason, null);
    }

    public SearchTaskMemoryContext withFeedback(SearchFeedback feedback) {
        return new SearchTaskMemoryContext(reportMonth, targetStartDate, targetEndDate, keywords,
                selectedSources, executedSources, counts, candidatePolicyIds, sourceFailures, currentRound,
                completedRounds, executedPlans, topicCoverage, consecutiveNoGrowthRounds, stopReason, feedback);
    }

    public record Counts(int found, int saved, int duplicate, int filtered, int failed) {
        public static Counts of(int found, int saved, int duplicate, int filtered, int failed) {
            return new Counts(found, saved, duplicate, filtered, failed);
        }

        public static Counts zero() {
            return new Counts(0, 0, 0, 0, 0);
        }
    }

    public record SourceFailure(String sourceId, String sourceName, String message) {
    }

    public record ExecutedPlan(List<String> keywords, List<String> sources) {
    }

    public record TopicCoverage(String topic, int matchedCandidateCount, String status, List<Long> evidencePolicyIds) {
    }
}
