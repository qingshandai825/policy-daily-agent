package com.itheima.policydailyagent.config;

import com.itheima.policydailyagent.service.search.MultiRoundConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 多轮搜索配置装配：将 {@link PolicySearchMultiRoundProperties} 规范化为
 * {@link MultiRoundConfig}（带边界校验与硬上限），供编排器与多轮服务注入；
 * 同时暴露可控时钟 {@link Clock}，供执行租约过期测试注入固定时钟。
 */
@Configuration
public class SearchMultiRoundConfiguration {

    @Bean
    public MultiRoundConfig multiRoundConfig(PolicySearchMultiRoundProperties properties) {
        return MultiRoundConfig.of(properties);
    }

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
