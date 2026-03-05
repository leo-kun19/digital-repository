CREATE TABLE IF NOT EXISTS download_event (
  id BIGSERIAL PRIMARY KEY,
  published_item_id BIGINT NOT NULL REFERENCES published_item(id),
  user_id BIGINT NOT NULL REFERENCES users(id),
  downloaded_at TIMESTAMP NOT NULL,
  ip_address VARCHAR(64),
  user_agent TEXT
);

CREATE INDEX IF NOT EXISTS idx_download_event_item ON download_event(published_item_id);
CREATE INDEX IF NOT EXISTS idx_download_event_user ON download_event(user_id);
