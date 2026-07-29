CREATE TABLE record_restore_operations (
  id CHAR(36) NOT NULL PRIMARY KEY,
  record_id CHAR(36) NOT NULL,
  source_revision_id CHAR(36) NOT NULL,
  actor_id CHAR(36) NOT NULL,
  before_record_version BIGINT NOT NULL,
  after_record_version BIGINT NOT NULL,
  before_content_hash CHAR(64) NOT NULL,
  after_content_hash CHAR(64) NOT NULL,
  diff_summary_json TEXT NOT NULL,
  idempotency_key VARCHAR(160) NOT NULL,
  payload_hash CHAR(64) NOT NULL,
  restored_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT uk_restore_record_key UNIQUE(record_id,idempotency_key),
  CONSTRAINT fk_restore_record FOREIGN KEY(record_id) REFERENCES experiment_records(id),
  CONSTRAINT fk_restore_revision FOREIGN KEY(source_revision_id) REFERENCES record_revisions(id),
  CONSTRAINT fk_restore_actor FOREIGN KEY(actor_id) REFERENCES users(id),
  CONSTRAINT ck_restore_version_increment CHECK(after_record_version > before_record_version)
);

CREATE INDEX idx_restore_record_time ON record_restore_operations(record_id,restored_at DESC);
CREATE INDEX idx_restore_source_revision ON record_restore_operations(source_revision_id);
