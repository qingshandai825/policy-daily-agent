package com.itheima.policydailyagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 多轮政策搜索第一阶段配置。默认关闭（单轮），需显式启用且请求显式携带
 * multiRoundEnabled=true 才会走多轮流程，从而保证既有单轮调用行为不变。
 */
@ConfigurationProperties(prefix = "policy.search.multi-round")
public class PolicySearchMultiRoundProperties {

    private boolean enabled = false;
    private int maxRounds = 3;
    private int noGrowthRounds = 2;
    private int coverageThreshold = 1;
    private int maxKeywordsPerRound = 5;
    private int maxContentScanLength = 2000;
    private List<String> topics = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxRounds() {
        return maxRounds;
    }

    public void setMaxRounds(int maxRounds) {
        this.maxRounds = maxRounds;
    }

    public int getNoGrowthRounds() {
        return noGrowthRounds;
    }

    public void setNoGrowthRounds(int noGrowthRounds) {
        this.noGrowthRounds = noGrowthRounds;
    }

    public int getCoverageThreshold() {
        return coverageThreshold;
    }

    public void setCoverageThreshold(int coverageThreshold) {
        this.coverageThreshold = coverageThreshold;
    }

    public int getMaxKeywordsPerRound() {
        return maxKeywordsPerRound;
    }

    public void setMaxKeywordsPerRound(int maxKeywordsPerRound) {
        this.maxKeywordsPerRound = maxKeywordsPerRound;
    }

    public int getMaxContentScanLength() {
        return maxContentScanLength;
    }

    public void setMaxContentScanLength(int maxContentScanLength) {
        this.maxContentScanLength = maxContentScanLength;
    }

    public List<String> getTopics() {
        return topics;
    }

    public void setTopics(List<String> topics) {
        this.topics = topics == null ? new ArrayList<>() : topics;
    }
}
