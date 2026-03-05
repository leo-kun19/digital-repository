ALTER TABLE case_supervisor
  ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;

ALTER TABLE case_supervisor
  ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMP;

ALTER TABLE case_supervisor
  ADD COLUMN IF NOT EXISTS decision_note VARCHAR(1000);

CREATE INDEX IF NOT EXISTS idx_case_supervisor_case_lecturer
  ON case_supervisor (case_id, lecturer_user_id);
