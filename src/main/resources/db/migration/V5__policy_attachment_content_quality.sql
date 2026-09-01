ALTER TABLE policy_document
    ADD COLUMN IF NOT EXISTS content_completeness VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN';

ALTER TABLE policy_document
    ADD COLUMN IF NOT EXISTS content_quality_reason VARCHAR(1000);

ALTER TABLE policy_document
    ADD COLUMN IF NOT EXISTS attachment_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE policy_document
    ADD COLUMN IF NOT EXISTS extracted_attachment_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE policy_document
    ADD COLUMN IF NOT EXISTS content_refreshed_at TIMESTAMP;

CREATE TABLE IF NOT EXISTS policy_attachment (
    id BIGSERIAL PRIMARY KEY,
    policy_id BIGINT NOT NULL REFERENCES policy_document(id) ON DELETE CASCADE,
    file_name VARCHAR(500) NOT NULL,
    source_url VARCHAR(1500) NOT NULL,
    file_type VARCHAR(30),
    content_type VARCHAR(200),
    extraction_status VARCHAR(30) NOT NULL DEFAULT 'DETECTED',
    extracted_content TEXT,
    content_hash VARCHAR(64),
    content_length INTEGER,
    error_message VARCHAR(2000),
    detected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    extracted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_policy_attachment_policy_url UNIQUE (policy_id, source_url)
);

CREATE INDEX IF NOT EXISTS idx_policy_attachment_policy
    ON policy_attachment(policy_id);

CREATE INDEX IF NOT EXISTS idx_policy_attachment_status
    ON policy_attachment(extraction_status, created_at);

COMMENT ON COLUMN policy_document.content_completeness IS
    'UNKNOWN/COMPLETE/PARTIAL/PAGE_ONLY/FAILED; only COMPLETE is eligible for Agent analysis';

COMMENT ON COLUMN policy_document.cleaned_content IS
    'Cleaned page text plus successfully extracted policy attachment text';

COMMENT ON TABLE policy_attachment IS
    'Traceable metadata and extracted text for policy PDF/DOCX/OFD attachments';
