package com.itheima.policydailyagent.agent.tool.impl;

import com.itheima.policydailyagent.agent.tool.AgentTool;
import com.itheima.policydailyagent.agent.tool.ToolRetryPolicy;
import com.itheima.policydailyagent.dto.PolicyCrawlResult;
import com.itheima.policydailyagent.service.PolicyCrawlerService;
import org.springframework.stereotype.Component;

@Component
public class PolicyCrawlerAgentTool implements AgentTool<String, PolicyCrawlResult> {

    public static final String NAME = "policy.crawl";

    private final PolicyCrawlerService policyCrawlerService;

    public PolicyCrawlerAgentTool(PolicyCrawlerService policyCrawlerService) {
        this.policyCrawlerService = policyCrawlerService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Fetch one policy page and extract source metadata, date, content and evidence";
    }

    @Override
    public PolicyCrawlResult execute(String url) {
        return policyCrawlerService.crawl(url);
    }

    @Override
    public ToolRetryPolicy retryPolicy() {
        return ToolRetryPolicy.externalCall();
    }

    @Override
    public String summarizeOutput(PolicyCrawlResult output) {
        return "title=" + output.title()
                + ", publishDate=" + output.publishDate()
                + ", source=" + output.sourceName();
    }
}
