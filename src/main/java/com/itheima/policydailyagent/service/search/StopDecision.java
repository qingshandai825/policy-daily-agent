package com.itheima.policydailyagent.service.search;

/**
 * 停止策略的决策结果。shouldStop=false 表示继续下一轮，reason 为 null；
 * 停止时 reason 为停止原因（MAX_ROUNDS_REACHED / ALL_TOPICS_COVERED /
 * NO_NEW_PLAN / NO_GROWTH / TASK_ERROR）。
 */
public record StopDecision(boolean shouldStop, String reason) {

    public static StopDecision continueRunning() {
        return new StopDecision(false, null);
    }

    public static StopDecision stop(String reason) {
        return new StopDecision(true, reason);
    }
}
