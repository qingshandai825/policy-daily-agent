package com.itheima.policydailyagent.service.memory;

/**
 * 信源搜索事件的输入载荷（input_json），结构稳定、可校验。
 */
public record SourceSearchInput(String sourceId, String sourceName, int roundNo) {
}
