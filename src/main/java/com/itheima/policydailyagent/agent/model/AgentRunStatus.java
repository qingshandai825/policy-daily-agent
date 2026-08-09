package com.itheima.policydailyagent.agent.model;

public enum AgentRunStatus {
    CREATED,
    RUNNING,
    WAITING_REVIEW,
    READY_FOR_REPORT,
    GENERATING_REPORT,
    COMPLETED,
    PARTIAL_SUCCESS,
    FAILED
}
