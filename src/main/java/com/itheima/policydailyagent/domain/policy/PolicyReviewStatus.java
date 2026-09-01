package com.itheima.policydailyagent.domain.policy;

/**
 * 人工审核状态。只有 ACCEPTED 状态允许进入 Agent 深度分析。
 */
public enum PolicyReviewStatus {
    PENDING,
    ACCEPTED,
    REJECTED
}
