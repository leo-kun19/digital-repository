ALTER TABLE submission_version
  ADD COLUMN IF NOT EXISTS checklist_template_id BIGINT REFERENCES checklist_template(id);

UPDATE submission_version
SET checklist_template_id = (
  SELECT ct.id
  FROM publication_case pc
  JOIN checklist_template ct
    ON ct.publication_type = pc.type
   AND ct.is_active = TRUE
  WHERE pc.id = submission_version.case_id
)
WHERE checklist_template_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_submission_version_checklist_template
  ON submission_version (checklist_template_id);

CREATE INDEX IF NOT EXISTS idx_repo_author_name
  ON published_item (author_name);
