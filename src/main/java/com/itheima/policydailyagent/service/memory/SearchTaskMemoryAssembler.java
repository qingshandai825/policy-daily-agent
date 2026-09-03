package com.itheima.policydailyagent.service.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itheima.policydailyagent.domain.search.SearchTask;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 负责把 {@link SearchTask} 与搜索进度装配成稳定的 {@link SearchTaskMemoryContext}，
 * 并统一做 JSON 序列化/反序列化。context_json 的结构只在此处产生，
 * 从而避免各 Service 手工拼接不一致的 JSON。
 */
@Component
public class SearchTaskMemoryAssembler {

    private final ObjectMapper objectMapper;

    public SearchTaskMemoryAssembler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SearchTaskMemoryContext contextOf(
            SearchTask task,
            List<String> executedSources,
            List<Long> candidatePolicyIds,
            SearchTaskMemoryContext.Counts counts,
            List<SearchTaskMemoryContext.SourceFailure> sourceFailures
    ) {
        return contextOf(task, executedSources, candidatePolicyIds, counts, sourceFailures,
                0, List.of(), List.of(), List.of(), 0, null);
    }

    public SearchTaskMemoryContext contextOf(
            SearchTask task,
            List<String> executedSources,
            List<Long> candidatePolicyIds,
            SearchTaskMemoryContext.Counts counts,
            List<SearchTaskMemoryContext.SourceFailure> sourceFailures,
            int currentRound,
            List<Integer> completedRounds,
            List<SearchTaskMemoryContext.ExecutedPlan> executedPlans,
            List<SearchTaskMemoryContext.TopicCoverage> topicCoverage,
            int consecutiveNoGrowthRounds,
            String stopReason
    ) {
        return new SearchTaskMemoryContext(
                task.getReportMonth(),
                task.getTargetStartDate(),
                task.getTargetEndDate(),
                splitList(task.getKeywords()),
                splitList(task.getSourceIds()),
                copy(executedSources),
                counts == null ? SearchTaskMemoryContext.Counts.zero() : counts,
                copy(candidatePolicyIds),
                copy(sourceFailures),
                currentRound,
                copy(completedRounds),
                copy(executedPlans),
                copy(topicCoverage),
                consecutiveNoGrowthRounds,
                stopReason
        );
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法序列化 Agent Memory 上下文", e);
        }
    }

    public SearchTaskMemoryContext parseContext(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, SearchTaskMemoryContext.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 解析任意 JSON 到指定类型（如 search_task.run_params_json → SearchRunParams）。
     * 空值或解析失败返回 null，由调用方决定兼容策略（缺失时拒绝而非臆造默认）。
     */
    public <T> T parseJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 解析字符串列表 JSON（如轮次记录的 keywords_json / target_sources_json）。
     * 解析失败或空值时返回空列表，保证恢复路径对损坏数据不抛异常。
     */
    public List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> parsed = objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
            return parsed == null ? List.of() : parsed;
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private List<String> splitList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private static <T> List<T> copy(List<T> value) {
        return value == null || value.isEmpty() ? List.of() : List.copyOf(value);
    }
}
