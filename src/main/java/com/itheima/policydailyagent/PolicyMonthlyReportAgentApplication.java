package com.itheima.policydailyagent;

import com.itheima.policydailyagent.config.PolicySourceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(PolicySourceProperties.class)
public class PolicyMonthlyReportAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PolicyMonthlyReportAgentApplication.class, args);
    }
}
