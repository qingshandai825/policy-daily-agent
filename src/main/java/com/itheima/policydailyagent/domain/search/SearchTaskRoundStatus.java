package com.itheima.policydailyagent.domain.search;

/**
 * 搜索任务轮次状态。轮次记录不可变（append-only），已完成轮次不可重复执行。
 */
public enum SearchTaskRoundStatus {
    PLANNED,
    RUNNING,
    COMPLETED,
    PARTIAL_FAILED,
    FAILED
}
