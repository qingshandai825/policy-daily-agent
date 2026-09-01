ALTER TABLE search_task ADD COLUMN IF NOT EXISTS failure_message TEXT;

CREATE TABLE IF NOT EXISTS search_task_policy (
    id BIGSERIAL PRIMARY KEY,
    search_task_id BIGINT NOT NULL REFERENCES search_task(id) ON DELETE CASCADE,
    policy_id BIGINT NOT NULL REFERENCES policy_document(id) ON DELETE CASCADE,
    source_id VARCHAR(100),
    provider VARCHAR(50) NOT NULL DEFAULT 'FIXED_SOURCE',
    discovered_url VARCHAR(1000) NOT NULL,
    discovered_title VARCHAR(500),
    search_snippet TEXT,
    discovery_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_search_task_policy UNIQUE (search_task_id, policy_id)
);

CREATE INDEX IF NOT EXISTS idx_search_task_policy_task
    ON search_task_policy(search_task_id, discovery_order);
CREATE INDEX IF NOT EXISTS idx_search_task_policy_policy
    ON search_task_policy(policy_id);

INSERT INTO search_task_policy (
    search_task_id, policy_id, source_id, provider, discovered_url,
    discovered_title, search_snippet, discovery_order, created_at
)
SELECT
    p.search_task_id,
    p.id,
    NULL,
    'LEGACY_MIGRATION',
    p.source_url,
    p.title,
    NULL,
    0,
    COALESCE(p.created_at, CURRENT_TIMESTAMP)
FROM policy_document p
WHERE p.search_task_id IS NOT NULL
ON CONFLICT (search_task_id, policy_id) DO NOTHING;
