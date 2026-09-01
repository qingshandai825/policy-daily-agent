CREATE TABLE IF NOT EXISTS search_task (
    id BIGSERIAL PRIMARY KEY,
    task_name VARCHAR(200) NOT NULL,
    report_month VARCHAR(7),
    target_start_date DATE NOT NULL,
    target_end_date DATE NOT NULL,
    keywords TEXT,
    source_ids TEXT,
    source_url VARCHAR(1000),
    status VARCHAR(30) NOT NULL DEFAULT 'CREATED',
    found_count INTEGER NOT NULL DEFAULT 0,
    saved_count INTEGER NOT NULL DEFAULT 0,
    duplicate_count INTEGER NOT NULL DEFAULT 0,
    filtered_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO search_task (
    id, task_name, report_month, target_start_date, target_end_date, keywords,
    source_url, status, found_count, saved_count, duplicate_count, filtered_count,
    failed_count, started_at, completed_at, created_at, updated_at
)
SELECT
    id,
    task_name,
    CASE
        WHEN target_start_date IS NOT NULL
             AND target_end_date IS NOT NULL
             AND date_trunc('month', target_start_date) = date_trunc('month', target_end_date)
        THEN to_char(target_start_date, 'YYYY-MM')
        ELSE NULL
    END,
    COALESCE(target_start_date, target_end_date, CURRENT_DATE),
    COALESCE(target_end_date, target_start_date, CURRENT_DATE),
    topics,
    source_url,
    CASE
        WHEN status IN ('CREATED', 'RUNNING', 'COMPLETED', 'PARTIAL_FAILED') THEN status
        ELSE 'COMPLETED'
    END,
    found_count,
    saved_count,
    duplicate_count,
    filtered_count,
    failed_count,
    started_at,
    completed_at,
    created_at,
    COALESCE(updated_at, created_at, CURRENT_TIMESTAMP)
FROM daily_task
ON CONFLICT (id) DO NOTHING;

SELECT setval(
    pg_get_serial_sequence('search_task', 'id'),
    COALESCE(MAX(id), 1),
    MAX(id) IS NOT NULL
)
FROM search_task;

ALTER TABLE policy_document ADD COLUMN IF NOT EXISTS cleaned_content TEXT;
ALTER TABLE policy_document ADD COLUMN IF NOT EXISTS policy_type VARCHAR(100);
ALTER TABLE policy_document ADD COLUMN IF NOT EXISTS keywords TEXT;
ALTER TABLE policy_document ADD COLUMN IF NOT EXISTS relevance_score NUMERIC(5, 2);
ALTER TABLE policy_document ADD COLUMN IF NOT EXISTS search_task_id BIGINT;
ALTER TABLE policy_document ADD COLUMN IF NOT EXISTS analysis_status VARCHAR(30);

UPDATE policy_document
SET search_task_id = daily_task_id
WHERE search_task_id IS NULL AND daily_task_id IS NOT NULL;

UPDATE policy_document
SET cleaned_content = content
WHERE cleaned_content IS NULL AND content IS NOT NULL;

UPDATE policy_document
SET review_status = CASE review_status
    WHEN 'APPROVED' THEN 'ACCEPTED'
    WHEN 'PENDING_REVIEW' THEN 'PENDING'
    WHEN 'NEEDS_EDIT' THEN 'PENDING'
    WHEN 'ACCEPTED' THEN 'ACCEPTED'
    WHEN 'REJECTED' THEN 'REJECTED'
    WHEN 'DEFERRED' THEN 'DEFERRED'
    ELSE 'PENDING'
END;

UPDATE policy_document
SET analysis_status = CASE
    WHEN status = 'SUMMARIZED' THEN 'ANALYZED'
    WHEN review_status = 'ACCEPTED' THEN 'READY'
    ELSE 'NOT_ANALYZED'
END
WHERE analysis_status IS NULL;

ALTER TABLE policy_document ALTER COLUMN review_status TYPE VARCHAR(30);
ALTER TABLE policy_document ALTER COLUMN review_status SET DEFAULT 'PENDING';
ALTER TABLE policy_document ALTER COLUMN review_status SET NOT NULL;
ALTER TABLE policy_document ALTER COLUMN analysis_status SET DEFAULT 'NOT_ANALYZED';
ALTER TABLE policy_document ALTER COLUMN analysis_status SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_policy_search_task ON policy_document(search_task_id);
CREATE INDEX IF NOT EXISTS idx_policy_review_status ON policy_document(review_status);
CREATE INDEX IF NOT EXISTS idx_policy_analysis_status ON policy_document(analysis_status);
CREATE INDEX IF NOT EXISTS idx_search_task_status ON search_task(status);
CREATE INDEX IF NOT EXISTS idx_search_task_report_month ON search_task(report_month);
CREATE INDEX IF NOT EXISTS idx_search_task_date_range
    ON search_task(target_start_date, target_end_date);

CREATE TABLE IF NOT EXISTS policy_review (
    id BIGSERIAL PRIMARY KEY,
    policy_id BIGINT NOT NULL REFERENCES policy_document(id) ON DELETE CASCADE,
    previous_status VARCHAR(30),
    review_status VARCHAR(30) NOT NULL,
    reviewed_by VARCHAR(100) NOT NULL,
    review_comment VARCHAR(1000),
    reviewed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_policy_review_policy
    ON policy_review(policy_id, reviewed_at);

CREATE TABLE IF NOT EXISTS policy_analysis (
    id BIGSERIAL PRIMARY KEY,
    policy_id BIGINT NOT NULL REFERENCES policy_document(id) ON DELETE CASCADE,
    version_no INTEGER NOT NULL,
    run_status VARCHAR(30) NOT NULL,
    input_hash VARCHAR(64) NOT NULL,
    model_name VARCHAR(100),
    prompt_version VARCHAR(50) NOT NULL,
    basic_info_json TEXT,
    core_content TEXT,
    relevant_content TEXT,
    recommended_section_code VARCHAR(100),
    recommendation_reason TEXT,
    generated_content TEXT,
    evidence_json TEXT,
    error_message TEXT,
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    CONSTRAINT uk_policy_analysis_version UNIQUE (policy_id, version_no)
);
CREATE INDEX IF NOT EXISTS idx_policy_analysis_policy
    ON policy_analysis(policy_id, created_at);

CREATE TABLE IF NOT EXISTS report_section (
    id BIGSERIAL PRIMARY KEY,
    section_code VARCHAR(100) NOT NULL UNIQUE,
    parent_id BIGINT REFERENCES report_section(id),
    section_name VARCHAR(200) NOT NULL,
    section_level INTEGER NOT NULL,
    sort_order INTEGER NOT NULL,
    writing_guide TEXT,
    key_point_placeholder VARCHAR(100),
    content_placeholder VARCHAR(100),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_report_section_parent_sort
    ON report_section(parent_id, sort_order);

CREATE TABLE IF NOT EXISTS report_template (
    id BIGSERIAL PRIMARY KEY,
    template_version VARCHAR(50) NOT NULL UNIQUE,
    resource_path VARCHAR(500) NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS monthly_report (
    id BIGSERIAL PRIMARY KEY,
    report_year INTEGER NOT NULL,
    report_month INTEGER NOT NULL,
    title VARCHAR(300) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    template_version VARCHAR(50),
    template_hash VARCHAR(64),
    content_confirmed_by VARCHAR(100),
    content_confirmed_at TIMESTAMP,
    generated_at TIMESTAMP,
    output_file_name VARCHAR(500),
    output_hash VARCHAR(64),
    lock_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_monthly_report_period UNIQUE (report_year, report_month),
    CONSTRAINT ck_monthly_report_month CHECK (report_month BETWEEN 1 AND 12)
);

CREATE TABLE IF NOT EXISTS monthly_report_item (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES monthly_report(id) ON DELETE CASCADE,
    section_id BIGINT NOT NULL REFERENCES report_section(id),
    policy_id BIGINT REFERENCES policy_document(id),
    analysis_id BIGINT REFERENCES policy_analysis(id),
    source_type VARCHAR(30) NOT NULL,
    item_title VARCHAR(500),
    agent_draft TEXT,
    final_content TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    sort_order INTEGER NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    lock_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_report_item_report_section
    ON monthly_report_item(report_id, section_id, sort_order);

CREATE TABLE IF NOT EXISTS monthly_report_item_revision (
    id BIGSERIAL PRIMARY KEY,
    report_item_id BIGINT NOT NULL REFERENCES monthly_report_item(id) ON DELETE CASCADE,
    revision_no INTEGER NOT NULL,
    content TEXT NOT NULL,
    change_type VARCHAR(50) NOT NULL,
    editor VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_report_item_revision UNIQUE (report_item_id, revision_no)
);
CREATE INDEX IF NOT EXISTS idx_report_item_revision_item
    ON monthly_report_item_revision(report_item_id, created_at);

CREATE TABLE IF NOT EXISTS monthly_report_item_source (
    id BIGSERIAL PRIMARY KEY,
    report_item_id BIGINT NOT NULL REFERENCES monthly_report_item(id) ON DELETE CASCADE,
    source_type VARCHAR(30) NOT NULL,
    policy_id BIGINT REFERENCES policy_document(id),
    source_title VARCHAR(500),
    source_url VARCHAR(1000),
    source_content_snapshot TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_report_item_source_item
    ON monthly_report_item_source(report_item_id);

INSERT INTO report_section (
    section_code, parent_id, section_name, section_level, sort_order,
    writing_guide, key_point_placeholder, content_placeholder
)
VALUES (
    'WORK_DYNAMICS', NULL, '一、重点工作动态', 1, 100,
    '汇总国家层面和山东省内与人工智能赋能制造业相关的重点政策及工作推进动态。',
    NULL, NULL
)
ON CONFLICT (section_code) DO NOTHING;

INSERT INTO report_section (
    section_code, parent_id, section_name, section_level, sort_order,
    writing_guide, key_point_placeholder, content_placeholder
)
VALUES
(
    'NATIONAL',
    (SELECT id FROM report_section WHERE section_code = 'WORK_DYNAMICS'),
    '（一）国家重点事项', 2, 110,
    '聚焦国家层面重大会议、重要讲话、政策文件、专项行动。采用“标题+正文”，正文按“时间/主体—核心内容—重点任务”展开。',
    '{{KEY_POINTS_NATIONAL}}', '{{SECTION_NATIONAL}}'
),
(
    'PROVINCIAL',
    (SELECT id FROM report_section WHERE section_code = 'WORK_DYNAMICS'),
    '（二）省内工作推进', 2, 120,
    '聚焦山东省内政策部署、会议培训、供需对接、产业链活动、能力中心建设、先锋应用培育。正文按“工作事项—推进情况—阶段成效”展开。',
    '{{KEY_POINTS_PROVINCIAL}}', '{{SECTION_PROVINCIAL}}'
),
(
    'PIONEER_PROGRESS', NULL, '二、先锋应用动态', 1, 200,
    '围绕“人工智能+制造”先锋应用工作总体进展、指标完成、问题帮扶、平台资源对接和典型经验，采用“总体情况+指标进展+问题帮扶+典型经验”结构。',
    '{{KEY_POINTS_PIONEER}}', NULL
),
(
    'PIONEER_OVERALL',
    (SELECT id FROM report_section WHERE section_code = 'PIONEER_PROGRESS'),
    '（一）总体推进情况', 2, 210,
    '说明各地市、重点企业和先锋应用方案推进进度，重点写企业改造、能力建设、数据治理、专项培训、宣传推广等总体情况。',
    NULL, '{{SECTION_PIONEER_OVERALL}}'
),
(
    'PIONEER_INDICATORS',
    (SELECT id FROM report_section WHERE section_code = 'PIONEER_PROGRESS'),
    '（二）重点指标完成情况', 2, 220,
    '说明核心指标总体完成率，以及规上企业改造、营收过亿元企业人工智能普及、DCMM贯标、产品培育等指标完成情况。',
    NULL, '{{SECTION_PIONEER_INDICATORS}}'
),
(
    'PIONEER_SUPPORT',
    (SELECT id FROM report_section WHERE section_code = 'PIONEER_PROGRESS'),
    '（三）问题帮扶和典型案例', 2, 230,
    '说明调度发现的问题及资源需求，并写明专题调研、专家辅导、供需对接、资源池推荐等帮扶措施和闭环机制。',
    NULL, '{{SECTION_PIONEER_SUPPORT}}'
),
(
    'PIONEER_EXPERIENCE',
    (SELECT id FROM report_section WHERE section_code = 'PIONEER_PROGRESS'),
    '（四）典型经验和创新做法', 2, 240,
    '从可全省复制推广、行业示范、资源整合等角度总结地市、行业或企业形成的模式和创新做法。',
    NULL, '{{SECTION_PIONEER_EXPERIENCE}}'
),
(
    'CASE', NULL, '三、典型应用案例', 1, 300,
    '展示山东省内企业、平台或行业解决方案的人工智能+制造典型实践。选取场景明确、技术路径清晰、应用成效可复核、具备示范价值的1—2个案例。',
    '{{KEY_POINTS_CASE}}', '{{SECTION_CASE}}'
),
(
    'TREND', NULL, '四、产业趋势洞察', 1, 400,
    '围绕技术、产品、市场、生态、标准等变化研判工业人工智能趋势。每期选取3个趋势点，按“趋势标题—主要表现—展望”组织。',
    '{{KEY_POINTS_TREND}}', '{{SECTION_TREND}}'
)
ON CONFLICT (section_code) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    section_name = EXCLUDED.section_name,
    section_level = EXCLUDED.section_level,
    sort_order = EXCLUDED.sort_order,
    writing_guide = EXCLUDED.writing_guide,
    key_point_placeholder = EXCLUDED.key_point_placeholder,
    content_placeholder = EXCLUDED.content_placeholder,
    active = TRUE,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO report_template (
    template_version, resource_path, sha256, active
)
VALUES (
    'v1-reference',
    'templates/monthly_report_template_v1.docx',
    '6A711D93D3542E19142C01522C938071457492014EC2671F109D51EAA0E91120',
    FALSE
)
ON CONFLICT (template_version) DO UPDATE SET
    resource_path = EXCLUDED.resource_path,
    sha256 = EXCLUDED.sha256;

INSERT INTO report_template (
    template_version, resource_path, sha256, active
)
VALUES (
    'v2-no-attachment',
    'templates/monthly_report_template.docx',
    '28B6D1314C04521444089A5B623266FEDF674C3A77FE8DA893CA3F623E5508A2',
    TRUE
)
ON CONFLICT (template_version) DO UPDATE SET
    resource_path = EXCLUDED.resource_path,
    sha256 = EXCLUDED.sha256,
    active = EXCLUDED.active;
