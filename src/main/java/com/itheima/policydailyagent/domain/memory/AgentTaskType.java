package com.itheima.policydailyagent.domain.memory;

/**
 * Agent 任务类型。第一阶段仅正式接入 POLICY_SEARCH（政策候选搜索），
 * 数据模型已预留 POLICY_ANALYSIS、REPORT_WRITING 供后续扩展。
 */
public enum AgentTaskType {
    POLICY_SEARCH,
    POLICY_ANALYSIS,
    REPORT_WRITING
}
