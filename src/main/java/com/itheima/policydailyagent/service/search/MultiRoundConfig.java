package com.itheima.policydailyagent.service.search;

import com.itheima.policydailyagent.config.PolicySearchMultiRoundProperties;

import java.util.List;

/**
 * 多轮搜索的规范化配置。构造时做边界校验并强制硬上限，防止无限循环。
 * 由 {@link #of(PolicySearchMultiRoundProperties)} 统一构建，配置非法时快速失败。
 */
public record MultiRoundConfig(
        boolean enabled,
        int maxRounds,
        int noGrowthRounds,
        int coverageThreshold,
        int maxKeywordsPerRound,
        int maxContentScanLength,
        List<String> topics
) {

    /** 最大轮次硬上限，防止配置过大导致无限循环。 */
    public static final int HARD_MAX_ROUNDS = 20;

    public static MultiRoundConfig of(PolicySearchMultiRoundProperties properties) {
        if (properties == null) {
            return disabled();
        }
        List<String> topics = (properties.getTopics() == null || properties.getTopics().isEmpty())
                ? SearchTopicDictionary.DEFAULT_TOPICS
                : properties.getTopics().stream()
                        .filter(topic -> topic != null && !topic.trim().isEmpty())
                        .map(String::trim)
                        .distinct()
                        .toList();
        return new MultiRoundConfig(
                properties.isEnabled(),
                requireRange(properties.getMaxRounds(), 1, HARD_MAX_ROUNDS, "max-rounds"),
                requireRange(properties.getNoGrowthRounds(), 1, 100, "no-growth-rounds"),
                requireRange(properties.getCoverageThreshold(), 1, 10_000, "coverage-threshold"),
                requireRange(properties.getMaxKeywordsPerRound(), 1, 100, "max-keywords-per-round"),
                requireRange(properties.getMaxContentScanLength(), 0, 100_000, "max-content-scan-length"),
                List.copyOf(topics)
        );
    }

    public static MultiRoundConfig disabled() {
        return new MultiRoundConfig(false, 3, 2, 1, 5, 2000, SearchTopicDictionary.DEFAULT_TOPICS);
    }

    private static int requireRange(int value, int min, int max, String key) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    "多轮搜索配置非法：" + key + " 必须在 [" + min + ", " + max + "] 之间，当前值 " + value);
        }
        return value;
    }
}
