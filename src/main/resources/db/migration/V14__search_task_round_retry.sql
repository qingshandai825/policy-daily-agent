-- 多轮政策搜索可靠性修复：轮次中断后的「逻辑轮次 + 重试尝试」模型。
--
-- 原唯一约束 (search_task_id, round_no) 强制「逻辑轮次」与「物理执行记录」一一对应，
-- 导致中断轮次无法被原地重试：恢复时只能 round_no+1，从而跳过原始未完成计划，
-- 丢失用户自定义关键词。本迁移新增 retry_no 区分「同一逻辑轮次的第 N 次尝试」：
--   1) 恢复时重试原始未完成计划（复用 keywords_json / target_sources_json），而非重新规划；
--   2) 中断重试不再消耗 max-rounds 的逻辑轮次预算；
--   3) 对重试次数另设独立上限（见 SearchRoundService.MAX_ROUND_RETRIES）。
--
-- 只保存摘要性数据，绝不写政策正文、附件或任何敏感配置。

ALTER TABLE search_task_round ADD COLUMN IF NOT EXISTS retry_no INTEGER NOT NULL DEFAULT 0;

ALTER TABLE search_task_round DROP CONSTRAINT IF EXISTS uk_search_task_round_no;
ALTER TABLE search_task_round ADD CONSTRAINT uk_search_task_round_retry
    UNIQUE (search_task_id, round_no, retry_no);
