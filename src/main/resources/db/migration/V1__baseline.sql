CREATE TABLE IF NOT EXISTS daily_task (
    id BIGSERIAL PRIMARY KEY,
    task_name VARCHAR(200) NOT NULL,
    target_start_date DATE,
    target_end_date DATE,
    topics TEXT,
    source_url VARCHAR(1000),
    status VARCHAR(50) NOT NULL DEFAULT 'CREATED',
    found_count INTEGER NOT NULL DEFAULT 0,
    saved_count INTEGER NOT NULL DEFAULT 0,
    duplicate_count INTEGER NOT NULL DEFAULT 0,
    filtered_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    summarized_count INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS policy_document (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    source_name VARCHAR(200),
    publish_date DATE,
    source_url VARCHAR(1000) NOT NULL,
    content TEXT,
    summary TEXT,
    category VARCHAR(100),
    daily_task_id BIGINT,
    retrieved_at TIMESTAMP,
    source_domain VARCHAR(200),
    source_type VARCHAR(50),
    authority_level VARCHAR(50),
    date_source VARCHAR(100),
    date_text VARCHAR(200),
    date_confidence VARCHAR(50),
    content_hash VARCHAR(64),
    evidence_snippet TEXT,
    filter_status VARCHAR(50),
    filter_reason VARCHAR(500),
    review_status VARCHAR(50) DEFAULT 'PENDING_REVIEW',
    review_comment VARCHAR(1000),
    reviewed_by VARCHAR(100),
    reviewed_at TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'RAW',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_policy_source_url ON policy_document(source_url);
CREATE INDEX IF NOT EXISTS idx_policy_publish_date ON policy_document(publish_date);
CREATE INDEX IF NOT EXISTS idx_daily_task_status ON daily_task(status);
CREATE INDEX IF NOT EXISTS idx_daily_task_target_date
    ON daily_task(target_start_date, target_end_date);
