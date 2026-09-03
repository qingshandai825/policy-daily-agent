package com.itheima.policydailyagent.service.search;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 纯函数化的停止策略。满足任一条件即停止：
 *   1) 所有主题已覆盖（ALL_TOPICS_COVERED）；
 *   2) 达到最大轮次（MAX_ROUNDS_REACHED，硬上限由配置层保证）；
 *   3) 连续若干轮无新增 SearchTaskPolicy 关联（NO_GROWTH）；
 *   4) 无可执行的新关键词计划（NO_NEW_PLAN）。
 * 不可恢复错误（TASK_ERROR）由编排层在异常路径直接判定，不经由此处。
 */
@Component
public class SearchStopPolicy {

    public StopDecision decide(
            int currentRound,
            int maxRounds,
            List<TopicCoverageResult> coverage,
            int noGrowthRounds,
            int consecutiveNoGrowthRounds,
            boolean hasNextPlan
    ) {
        if (isAllCovered(coverage)) {
            return StopDecision.stop("ALL_TOPICS_COVERED");
        }
        if (currentRound >= maxRounds) {
            return StopDecision.stop("MAX_ROUNDS_REACHED");
        }
        if (consecutiveNoGrowthRounds >= noGrowthRounds) {
            return StopDecision.stop("NO_GROWTH");
        }
        if (!hasNextPlan) {
            return StopDecision.stop("NO_NEW_PLAN");
        }
        return StopDecision.continueRunning();
    }

    /**
     * 执行新轮次之前的停止检查（不含 NO_NEW_PLAN，该条件由规划器返回空计划判定）。
     * 在恢复路径中，若上一轮已触及上限，则在执行任何新轮次之前就返回停止原因，
     * 避免「resume 再多执行一轮超限」。
     */
    public Optional<String> checkBeforeExecution(
            int nextRoundNo,
            int maxRounds,
            List<TopicCoverageResult> coverage,
            int noGrowthRounds,
            int consecutiveNoGrowthRounds
    ) {
        if (isAllCovered(coverage)) {
            return Optional.of("ALL_TOPICS_COVERED");
        }
        if (nextRoundNo > maxRounds) {
            return Optional.of("MAX_ROUNDS_REACHED");
        }
        if (consecutiveNoGrowthRounds >= noGrowthRounds) {
            return Optional.of("NO_GROWTH");
        }
        return Optional.empty();
    }

    private boolean isAllCovered(List<TopicCoverageResult> coverage) {
        return coverage != null
                && !coverage.isEmpty()
                && coverage.stream().allMatch(result -> result.status() == CoverageStatus.COVERED);
    }
}
