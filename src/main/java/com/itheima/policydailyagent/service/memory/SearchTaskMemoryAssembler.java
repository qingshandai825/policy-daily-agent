package com.itheima.policydailyagent.service.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
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
        return new SearchTaskMemoryContext(
                task.getReportMonth(),
                task.getTargetStartDate(),
                task.getTargetEndDate(),
                splitList(task.getKeywords()),
                splitList(task.getSourceIds()),
                copy(executedSources),
                counts == null ? SearchTaskMemoryContext.Counts.zero() : counts,
                copy(candidatePolicyIds),
                copy(sourceFailures)
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
