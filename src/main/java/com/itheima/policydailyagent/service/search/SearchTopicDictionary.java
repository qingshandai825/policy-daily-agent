package com.itheima.policydailyagent.service.search;

import java.util.List;
import java.util.Map;

/**
 * 政策搜索主题词典的唯一事实来源。既作为单轮默认关键词，也作为多轮
 * 覆盖度评估与轮次规划的主题集合，避免在编排器与规划器中重复配置。
 */
public final class SearchTopicDictionary {

    public static final List<String> DEFAULT_TOPICS = List.of(
            "人工智能", "大模型", "智能体", "人工智能+", "数据集", "算力",
            "智能制造", "工业互联网", "数字化转型", "软件和信息技术"
    );

    private SearchTopicDictionary() {
    }

    /** 无材料时也能有界扩展检索；这些词是程序词典，绝不算模型抽取的证据。 */
    private static final Map<String, List<String>> SEARCH_TERMS = Map.of(
            "人工智能", List.of("人工智能", "智能工厂", "行业大模型"),
            "大模型", List.of("大模型", "工业大模型", "行业大模型"),
            "智能制造", List.of("智能制造", "智能工厂", "数字化车间"),
            "数字化转型", List.of("数字化转型", "数智化", "数字化改造"),
            "数据集", List.of("数据集", "高质量数据", "工业数据"),
            "算力", List.of("算力", "智算", "算力基础设施")
    );

    public static List<String> searchTerms(String topic) {
        return SEARCH_TERMS.getOrDefault(topic, List.of(topic));
    }
}
