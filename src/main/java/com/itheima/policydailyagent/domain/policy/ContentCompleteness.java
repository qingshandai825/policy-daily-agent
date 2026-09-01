package com.itheima.policydailyagent.domain.policy;

public enum ContentCompleteness {
    UNKNOWN,
    COMPLETE,
    PARTIAL,
    PAGE_ONLY,
    FAILED;

    public boolean isAnalyzable() {
        return this == COMPLETE;
    }
}
