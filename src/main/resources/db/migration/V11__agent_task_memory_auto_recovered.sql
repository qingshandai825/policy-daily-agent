-- 为“读取时自动补建”的 Memory 增加显式标记，避免前端仅凭 summary 文案猜测。
-- 补建快照仅用于追溯，不代表真实搜索进度；前端据此展示温和提示。

ALTER TABLE agent_task_memory
    ADD COLUMN IF NOT EXISTS auto_recovered BOOLEAN NOT NULL DEFAULT FALSE;
