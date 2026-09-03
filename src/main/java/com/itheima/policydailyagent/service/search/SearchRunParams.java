package com.itheima.policydailyagent.service.search;

import java.util.List;

/**
 * 一次多轮搜索任务首次运行时的实际生效参数快照，持久化在 search_task.run_params_json。
 * resume 据此恢复原始参数（而非当前默认配置），并在快照缺失/版本不兼容时明确拒绝。
 */
public record SearchRunParams(
        int version,
        int maxLinksPerSource,
        boolean filterByKeyword,
        boolean multiRoundEnabled,
        int maxRounds,
        int noGrowthRounds,
        int coverageThreshold,
        int maxKeywordsPerRound,
        int maxContentScanLength,
        List<String> topics
) {

    /** 参数快照结构版本，结构变化时递增。 */
    public static final int CURRENT_VERSION = 1;

    public static SearchRunParams of(
            int maxLinksPerSource,
            boolean filterByKeyword,
            boolean multiRoundEnabled,
            MultiRoundConfig config
    ) {
        return new SearchRunParams(
                CURRENT_VERSION,
                maxLinksPerSource,
                filterByKeyword,
                multiRoundEnabled,
                config.maxRounds(),
                config.noGrowthRounds(),
                config.coverageThreshold(),
                config.maxKeywordsPerRound(),
                config.maxContentScanLength(),
                config.topics()
        );
    }

    /** 用快照重建恢复期间使用的有效多轮配置（enabled 恒为 true）。 */
    public MultiRoundConfig toConfig() {
        return new MultiRoundConfig(
                true,
                maxRounds,
                noGrowthRounds,
                coverageThreshold,
                maxKeywordsPerRound,
                maxContentScanLength,
                topics == null ? List.of() : List.copyOf(topics)
        );
    }
}
