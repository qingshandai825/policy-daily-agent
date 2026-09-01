-- 移除审核状态 DEFERRED（暂缓）：审核门禁简化为二元「通过/不通过」。
-- review_status 为 VARCHAR，无需改类型；此处仅将历史残留 DEFERRED 回退为待审核 PENDING，
-- 并清理审核历史表中的 DEFERRED 记录，避免展示时出现未知状态。

UPDATE policy_document
SET review_status = 'PENDING'
WHERE review_status = 'DEFERRED';

UPDATE policy_review
SET review_status = 'PENDING'
WHERE review_status = 'DEFERRED';

UPDATE policy_review
SET previous_status = 'PENDING'
WHERE previous_status = 'DEFERRED';
