package com.itheima.policydailyagent.service.search;

/**
 * 主题覆盖度状态。NOT_COVERED 表示没有任何候选政策命中该主题，
 * INSUFFICIENT 表示命中数未达到阈值，COVERED 表示已充分覆盖。
 */
public enum CoverageStatus {
    COVERED,
    INSUFFICIENT,
    NOT_COVERED
}
