ALTER TABLE published_item
  ADD COLUMN IF NOT EXISTS program VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_repo_program ON published_item (program);
