package com.itheima.policydailyagent.service.search;

/**
 * 参与覆盖度评估的候选政策文本视图，只携带匹配所需字段，
 * 不要求评估器依赖 JPA 实体，保证评估逻辑纯函数化、可单测。
 */
public record CandidateText(
        Long policyId,
        String title,
        String keywords,
        String summary,
        String content
) {
}
