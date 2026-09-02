-- 任务级 Agent Memory：为搜索/分析/月报等 Agent 任务保存可持久化、可追溯、
-- 可恢复的任务上下文快照与事件流水。第一阶段仅接入 POLICY_SEARCH，
-- task_type 已预留 POLICY_ANALYSIS / REPORT_WRITING。
--
-- context_json / input_json / output_json 统一使用 TEXT（与既有 basic_info_json、
-- evidence_json 一致），由应用层 ObjectMapper 序列化，避免 H2 兼容问题。

CREATE TABLE IF NOT EXISTS agent_task_memory (
    id BIGSERIAL PRIMARY KEY,
    task_type VARCHAR(30) NOT NULL,
    business_task_id BIGINT NOT NULL,
    goal TEXT,
    current_phase VARCHAR(30) NOT NULL,
    memory_status VARCHAR(30) NOT NULL,
    summary TEXT,
    context_json TEXT,
    next_action TEXT,
    version_no BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agent_task_memory_task UNIQUE (task_type, business_task_id)
);

CREATE INDEX IF NOT EXISTS idx_agent_task_memory_type_status
    ON agent_task_memory(task_type, memory_status);

CREATE TABLE IF NOT EXISTS agent_task_event (
    id BIGSERIAL PRIMARY KEY,
    memory_id BIGINT NOT NULL REFERENCES agent_task_memory(id) ON DELETE CASCADE,
    round_no INTEGER NOT NULL DEFAULT 1,
    event_type VARCHAR(30) NOT NULL,
    input_json TEXT,
    output_json TEXT,
    decision TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_agent_task_event_memory
    ON agent_task_event(memory_id, id);
