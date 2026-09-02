package com.itheima.policydailyagent.domain.memory;

/**
 * Agent 任务 Memory 的持久化状态。
 * FAILED 表示任务异常但保留了失败原因与可恢复的 next_action。
 */
public enum AgentTaskMemoryStatus {
    ACTIVE,
    COMPLETED,
    FAILED
}
