package com.itheima.policydailyagent.domain.memory;

/**
 * Agent 任务事件类型。事件记录原则上不可变（append-only），
 * 只新增、不修改、不删除。
 */
public enum AgentTaskEventType {
    TASK_STARTED,
    SOURCE_SEARCHED,
    SOURCE_FAILED,
    ROUND_COMPLETED,
    TASK_COMPLETED,
    TASK_FAILED
}
