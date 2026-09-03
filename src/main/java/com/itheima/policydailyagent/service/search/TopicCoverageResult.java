package com.itheima.policydailyagent.service.search;

import java.util.List;

/**
 * 单个主题的覆盖度评估结果。evidencePolicyIds 记录命中该主题的候选政策 ID，
 * 用于追溯"为什么认为该主题已覆盖/未覆盖"。
 */
public record TopicCoverageResult(
        String topic,
        int matchedCandidateCount,
        CoverageStatus status,
        List<Long> evidencePolicyIds
) {
}
