-- 多轮政策搜索缺陷修复：恢复入口与执行权管理、失败信源排除、参数快照。
--
-- 1) search_task 增加执行租约字段（单机部署，靠 DB 租约区分「仍在执行」与「进程崩溃后中断」）：
--    - executor_id       当前持有执行权的执行器标识；
--    - lease_expires_at  租约过期时间（崩溃后到期即可被下一个 resume 接管）；
--    - termination_reason 正常完成/失败时记录的终止原因，用于拒绝「已正常完成」任务的恢复；
--    - run_params_json    首次运行时的实际生效参数快照，resume 据此恢复而非使用当前默认配置。
-- 2) search_task_source_failure 持久化任务内信源级失败（TLS/证书/连接等），
--    后续轮次规划与执行据此排除失败信源，恢复后依然有效。
--
-- 只保存摘要性数据，绝不写政策正文、附件或任何敏感配置；沿用 TEXT 存 JSON 约定。

ALTER TABLE search_task ADD COLUMN IF NOT EXISTS executor_id VARCHAR(100);
ALTER TABLE search_task ADD COLUMN IF NOT EXISTS lease_expires_at TIMESTAMP;
ALTER TABLE search_task ADD COLUMN IF NOT EXISTS termination_reason VARCHAR(50);
ALTER TABLE search_task ADD COLUMN IF NOT EXISTS run_params_json TEXT;

CREATE TABLE IF NOT EXISTS search_task_source_failure (
    id BIGSERIAL PRIMARY KEY,
    search_task_id BIGINT NOT NULL REFERENCES search_task(id) ON DELETE CASCADE,
    source_id VARCHAR(100) NOT NULL,
    source_name VARCHAR(200),
    message TEXT,
    first_round_no INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_search_task_source_failure UNIQUE (search_task_id, source_id)
);

CREATE INDEX IF NOT EXISTS idx_search_task_source_failure_task
    ON search_task_source_failure(search_task_id);
