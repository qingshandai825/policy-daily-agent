CREATE TABLE IF NOT EXISTS monthly_report_generation (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES monthly_report(id) ON DELETE CASCADE,
    generation_no INTEGER NOT NULL,
    issue_no INTEGER NOT NULL,
    total_issue_no INTEGER NOT NULL,
    report_month VARCHAR(50) NOT NULL,
    report_to VARCHAR(1000) NOT NULL,
    send_to VARCHAR(1000) NOT NULL,
    contact_info VARCHAR(1000) NOT NULL,
    generated_by VARCHAR(100) NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    content_type VARCHAR(150) NOT NULL,
    file_size BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    file_content BYTEA NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_report_generation_no UNIQUE (report_id, generation_no),
    CONSTRAINT ck_report_generation_issue CHECK (issue_no > 0 AND total_issue_no >= issue_no)
);

CREATE INDEX IF NOT EXISTS idx_report_generation_report
    ON monthly_report_generation(report_id, generation_no DESC);
