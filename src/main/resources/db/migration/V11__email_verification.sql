-- Add email_verified column to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Email verification OTP tokens
CREATE TABLE IF NOT EXISTS email_verification_token (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  otp_code VARCHAR(6) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMP NOT NULL,
  verified BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_evt_user_id ON email_verification_token (user_id);
CREATE INDEX IF NOT EXISTS idx_evt_otp_code ON email_verification_token (otp_code);
