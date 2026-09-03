-- 多轮政策搜索第一阶段：为一次搜索任务保存受控的轮次执行记录。
-- 每轮记录计划、关键词/信源集合、前后主题覆盖度快照与计数，支撑：
--   1) 轮次可追溯（哪一轮用了哪些关键词、覆盖了什么主题）；
--   2) 恢复（resume 从最大 round_no 续跑，不重复执行已完成轮次）。
--
-- 只保存摘要性 JSON，绝不写政策正文、附件或任何敏感配置。
-- 沿用既有 TEXT 存 JSON 的约定，避免 H2 兼容问题。

CREATE TABLE IF NOT EXISTS search_task_round (
    id BIGSERIAL PRIMARY KEY,
    search_task_id BIGINT NOT NULL REFERENCES search_task(id) ON DELETE CASCADE,
    round_no INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PLANNED',
    plan_json TEXT,
    keywords_json TEXT,
    target_sources_json TEXT,
    coverage_before_json TEXT,
    coverage_after_json TEXT,
    found_count INTEGER NOT NULL DEFAULT 0,
    saved_count INTEGER NOT NULL DEFAULT 0,
    duplicate_count INTEGER NOT NULL DEFAULT 0,
    filtered_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    new_association_count INTEGER NOT NULL DEFAULT 0,
    stop_reason VARCHAR(200),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_search_task_round_no UNIQUE (search_task_id, round_no)
);

CREATE INDEX IF NOT EXISTS idx_search_task_round_task
    ON search_task_round(search_task_id, round_no);
