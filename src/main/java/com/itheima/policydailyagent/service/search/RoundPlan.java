package com.itheima.policydailyagent.service.search;

import java.util.List;

/**
 * 一轮搜索的执行计划：关键词集合 + 信源集合。
 * 关键词来自主题词典（第一阶段不使用 LLM 生成 Query），信源在第一阶段保持
 * 与用户选定信源一致，后续轮次仅变化关键词集合。
 */
public record RoundPlan(List<String> keywords, List<String> sourceIds) {

    public RoundPlan {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds);
    }

    /**
     * 规范化签名，用于判断是否重复执行完全相同的"关键词集合 + 信源集合"计划。
     */
    public String signature() {
        String k = String.join("|", keywords.stream().sorted().toList());
        String s = String.join("|", sourceIds.stream().sorted().toList());
        return "keywords=[" + k + "];sources=[" + s + "]";
    }
}
