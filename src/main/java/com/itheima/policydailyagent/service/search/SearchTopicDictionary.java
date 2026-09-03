package com.itheima.policydailyagent.service.search;

import java.util.List;

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
}
