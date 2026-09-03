package com.itheima.policydailyagent.service.search;

import com.itheima.policydailyagent.config.PolicySearchMultiRoundProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MultiRoundConfigTests {

    @Test
    void disabledIsOffWithDefaultTopics() {
        MultiRoundConfig config = MultiRoundConfig.disabled();
        assertThat(config.enabled()).isFalse();
        assertThat(config.maxRounds()).isEqualTo(3);
        assertThat(config.topics()).isEqualTo(SearchTopicDictionary.DEFAULT_TOPICS);
    }

    @Test
    void ofNullPropertiesFallsBackToDisabled() {
        assertThat(MultiRoundConfig.of(null).enabled()).isFalse();
    }

    @Test
    void ofEmptyTopicsFallsBackToDefaultTopics() {
        PolicySearchMultiRoundProperties properties = new PolicySearchMultiRoundProperties();
        properties.setEnabled(true);
        properties.setTopics(List.of());
        MultiRoundConfig config = MultiRoundConfig.of(properties);
        assertThat(config.enabled()).isTrue();
        assertThat(config.topics()).isEqualTo(SearchTopicDictionary.DEFAULT_TOPICS);
    }

    @Test
    void ofCustomTopicsAreTrimmedAndDistinct() {
        PolicySearchMultiRoundProperties properties = new PolicySearchMultiRoundProperties();
        properties.setTopics(List.of(" 人工智能 ", "人工智能", "算力"));
        MultiRoundConfig config = MultiRoundConfig.of(properties);
        assertThat(config.topics()).containsExactly("人工智能", "算力");
    }

    @Test
    void ofRejectsMaxRoundsBelowOne() {
        PolicySearchMultiRoundProperties properties = new PolicySearchMultiRoundProperties();
        properties.setMaxRounds(0);
        assertThatThrownBy(() -> MultiRoundConfig.of(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-rounds");
    }

    @Test
    void ofRejectsMaxRoundsAboveHardCap() {
        PolicySearchMultiRoundProperties properties = new PolicySearchMultiRoundProperties();
        properties.setMaxRounds(MultiRoundConfig.HARD_MAX_ROUNDS + 1);
        assertThatThrownBy(() -> MultiRoundConfig.of(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-rounds");
    }

    @Test
    void ofRejectsNonPositiveNoGrowthRounds() {
        PolicySearchMultiRoundProperties properties = new PolicySearchMultiRoundProperties();
        properties.setNoGrowthRounds(0);
        assertThatThrownBy(() -> MultiRoundConfig.of(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no-growth-rounds");
    }

    @Test
    void ofRejectsNonPositiveCoverageThreshold() {
        PolicySearchMultiRoundProperties properties = new PolicySearchMultiRoundProperties();
        properties.setCoverageThreshold(0);
        assertThatThrownBy(() -> MultiRoundConfig.of(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("coverage-threshold");
    }
}
