ALTER TABLE policy_analysis
    ADD COLUMN IF NOT EXISTS generated_title VARCHAR(500);

CREATE INDEX IF NOT EXISTS idx_policy_analysis_run_status
    ON policy_analysis(run_status, created_at);

CREATE INDEX IF NOT EXISTS idx_report_item_analysis
    ON monthly_report_item(analysis_id);

ALTER TABLE monthly_report_item
    ADD CONSTRAINT uk_report_item_report_policy UNIQUE (report_id, policy_id);
