-- 扩展现有表，不增加业务表。旧任务/分析记录保持可读。
ALTER TABLE search_task_round ADD COLUMN IF NOT EXISTS search_feedback_json TEXT;
ALTER TABLE policy_analysis ADD COLUMN IF NOT EXISTS quality_report_json TEXT;
