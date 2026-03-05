CREATE TABLE IF NOT EXISTS audit_event (
  id BIGSERIAL PRIMARY KEY,
  case_id BIGINT NOT NULL REFERENCES publication_case(id),
  submission_version_id BIGINT REFERENCES submission_version(id),
  actor_user_id BIGINT REFERENCES users(id),
  actor_email VARCHAR(255),
  actor_role VARCHAR(50) NOT NULL,
  event_type VARCHAR(80) NOT NULL,
  message TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_event_case_created_at
  ON audit_event (case_id, created_at);

ALTER TABLE workflow_comment
  ADD COLUMN IF NOT EXISTS author_email VARCHAR(255);

UPDATE workflow_comment wc
SET author_email = (
  SELECT u.email
  FROM users u
  WHERE u.id = wc.author_user_id
)
WHERE wc.author_email IS NULL;
