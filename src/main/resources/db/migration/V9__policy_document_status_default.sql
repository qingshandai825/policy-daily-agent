-- 修复候选入库失败：policy_document.status 在基线中为 NOT NULL DEFAULT 'RAW'，
-- 但重构后 PolicyDocument 实体不再映射该列，且 policy_daily_agent 库中该列无默认值，
-- 导致每条候选 INSERT 均因 not-null 约束失败、候选池恒为 0。
-- 此处补默认值并回填，应用层也会在 createPolicyDocument 中显式写入 'RAW'。

ALTER TABLE policy_document ALTER COLUMN status SET DEFAULT 'RAW';
UPDATE policy_document SET status = 'RAW' WHERE status IS NULL;
