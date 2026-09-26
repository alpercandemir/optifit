CREATE UNLOGGED TABLE IF NOT EXISTS recommendation_jobs (
  id VARCHAR(36) PRIMARY KEY,
  owner VARCHAR(128) NOT NULL,
  request_key VARCHAR(80) NOT NULL,
  fingerprint VARCHAR(64) NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_at BIGINT NOT NULL,
  expires_at BIGINT NOT NULL,
  result_json TEXT,
  error_code VARCHAR(40),
  error_message VARCHAR(500),
  UNIQUE(owner, request_key)
);
CREATE INDEX IF NOT EXISTS jobs_expiry ON recommendation_jobs(expires_at);
