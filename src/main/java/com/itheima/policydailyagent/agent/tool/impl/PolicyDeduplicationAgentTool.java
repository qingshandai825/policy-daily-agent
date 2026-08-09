package com.itheima.policydailyagent.agent.tool.impl;

import com.itheima.policydailyagent.agent.dto.DeduplicationResult;
import com.itheima.policydailyagent.agent.dto.DeduplicationToolInput;
import com.itheima.policydailyagent.agent.tool.AgentTool;
import com.itheima.policydailyagent.repository.PolicyDocumentRepository;
import org.springframework.stereotype.Component;

@Component
public class PolicyDeduplicationAgentTool implements AgentTool<DeduplicationToolInput, DeduplicationResult> {

    public static final String NAME = "policy.deduplicate";

    private final PolicyDocumentRepository policyDocumentRepository;

    public PolicyDeduplicationAgentTool(PolicyDocumentRepository policyDocumentRepository) {
        this.policyDocumentRepository = policyDocumentRepository;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Detect duplicate policies by canonical source URL and content hash";
    }

    @Override
    public DeduplicationResult execute(DeduplicationToolInput input) {
        if (hasText(input.sourceUrl()) && policyDocumentRepository.existsBySourceUrl(input.sourceUrl())) {
            return new DeduplicationResult(true, "SOURCE_URL");
        }
        if (hasText(input.contentHash()) && policyDocumentRepository.existsByContentHash(input.contentHash())) {
            return new DeduplicationResult(true, "CONTENT_HASH");
        }
        return new DeduplicationResult(false, "UNIQUE");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
