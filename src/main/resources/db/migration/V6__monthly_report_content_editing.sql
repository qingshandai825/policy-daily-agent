ALTER TABLE monthly_report_item_revision
    ADD COLUMN IF NOT EXISTS item_title VARCHAR(500),
    ADD COLUMN IF NOT EXISTS section_id BIGINT,
    ADD COLUMN IF NOT EXISTS item_status VARCHAR(30),
    ADD COLUMN IF NOT EXISTS sort_order INTEGER,
    ADD COLUMN IF NOT EXISTS change_note VARCHAR(1000);

UPDATE monthly_report_item_revision revision
SET item_title = item.item_title,
    section_id = item.section_id,
    item_status = item.status,
    sort_order = item.sort_order
FROM monthly_report_item item
WHERE revision.report_item_id = item.id
  AND (revision.section_id IS NULL OR revision.item_status IS NULL OR revision.sort_order IS NULL);

CREATE INDEX IF NOT EXISTS idx_report_item_revision_no
    ON monthly_report_item_revision(report_item_id, revision_no DESC);
