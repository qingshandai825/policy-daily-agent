package com.itheima.policydailyagent.dto.memory;

import com.itheima.policydailyagent.domain.memory.AgentTaskMemoryStatus;
import com.itheima.policydailyagent.domain.memory.AgentTaskPhase;
import com.itheima.policydailyagent.domain.memory.AgentTaskType;
import com.itheima.policydailyagent.service.memory.SearchTaskMemoryContext;

import java.time.LocalDateTime;

/**
 * 只读的 Agent Memory 视图。context 为 context_json 解析后的结构化快照，
 * 若无法解析（例如未来其他任务类型的异构上下文）则为 null。
 */
public record AgentTaskMemoryView(
        Long id,
        AgentTaskType taskType,
        Long businessTaskId,
        String goal,
        AgentTaskPhase currentPhase,
        AgentTaskMemoryStatus memoryStatus,
        String summary,
        boolean autoRecovered,
        SearchTaskMemoryContext context,
        String nextAction,
        long versionNo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
