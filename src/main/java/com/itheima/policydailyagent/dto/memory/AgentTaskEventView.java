package com.itheima.policydailyagent.dto.memory;

import com.itheima.policydailyagent.domain.memory.AgentTaskEventType;

import java.time.LocalDateTime;

/**
 * 只读的 Agent 事件视图。input_json / output_json 为原始字符串，
 * 事件类型各异、结构异构，故不在此处强转。
 */
public record AgentTaskEventView(
        Long id,
        Long memoryId,
        int roundNo,
        AgentTaskEventType eventType,
        String inputJson,
        String outputJson,
        String decision,
        LocalDateTime createdAt
) {
}
