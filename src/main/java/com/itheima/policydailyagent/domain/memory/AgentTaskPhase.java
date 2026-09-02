package com.itheima.policydailyagent.domain.memory;

/**
 * Agent 任务当前阶段。第一阶段仅覆盖搜索任务的单轮生命周期，
 * 后续多轮自主搜索可在 SEARCHING 与 COMPLETED 之间扩展轮次阶段。
 */
public enum AgentTaskPhase {
    INITIALIZED,
    SEARCHING,
    COMPLETED,
    FAILED
}
