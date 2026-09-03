package com.itheima.policydailyagent.service.search;

import com.itheima.policydailyagent.service.memory.SearchTaskMemoryContext;

import java.util.List;

/**
 * 单轮执行的汇总结果，由 {@link SearchRoundExecutor} 返回，供单轮与多轮流程共用。
 */
public record RoundExecution(
        int found,
        int saved,
        int duplicate,
        int filtered,
        int failed,
        int associated,
        int successfulSources,
        List<String> executedSources,
        List<Long> candidatePolicyIds,
        List<SearchTaskMemoryContext.SourceFailure> sourceFailures,
        List<String> messages
) {
}
