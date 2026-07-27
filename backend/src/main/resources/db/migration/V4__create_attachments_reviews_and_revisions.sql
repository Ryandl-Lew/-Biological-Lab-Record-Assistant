CREATE TABLE attachments (
  id CHAR(36) NOT NULL PRIMARY KEY,
  record_id CHAR(36) NOT NULL,
  uploader_id CHAR(36) NOT NULL,
  original_filename VARCHAR(255) NOT NULL,
  storage_key CHAR(36) NOT NULL,
  media_type VARCHAR(120) NOT NULL,
  size_bytes BIGINT NOT NULL,
  previewable BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP(6) NOT NULL,
  deleted_at TIMESTAMP(6) NULL,
  CONSTRAINT uk_attachments_storage_key UNIQUE(storage_key),
  CONSTRAINT fk_attachments_record FOREIGN KEY(record_id) REFERENCES experiment_records(id),
  CONSTRAINT fk_attachments_uploader FOREIGN KEY(uploader_id) REFERENCES users(id)
);
CREATE INDEX idx_attachments_record_active ON attachments(record_id,deleted_at,created_at);

CREATE TABLE record_revisions (
  id CHAR(36) NOT NULL PRIMARY KEY,
  record_id CHAR(36) NOT NULL,
  revision_no INT NOT NULL,
  snapshot_json TEXT NOT NULL,
  content_hash CHAR(64) NOT NULL,
  submit_note VARCHAR(2000) NULL,
  submitted_by CHAR(36) NOT NULL,
  submitted_at TIMESTAMP(6) NOT NULL,
  idempotency_key VARCHAR(160) NULL,
  CONSTRAINT uk_record_revision_no UNIQUE(record_id,revision_no),
  CONSTRAINT uk_record_submission_key UNIQUE(record_id,idempotency_key),
  CONSTRAINT fk_revisions_record FOREIGN KEY(record_id) REFERENCES experiment_records(id),
  CONSTRAINT fk_revisions_submitter FOREIGN KEY(submitted_by) REFERENCES users(id)
);
CREATE INDEX idx_revisions_record_submitted ON record_revisions(record_id,submitted_at);

CREATE TABLE reviews (
  id CHAR(36) NOT NULL PRIMARY KEY,
  record_id CHAR(36) NOT NULL,
  revision_id CHAR(36) NOT NULL,
  reviewer_id CHAR(36) NOT NULL,
  status VARCHAR(30) NOT NULL,
  decision_comment VARCHAR(3000) NULL,
  assigned_at TIMESTAMP(6) NOT NULL,
  decided_at TIMESTAMP(6) NULL,
  CONSTRAINT uk_review_revision UNIQUE(revision_id),
  CONSTRAINT fk_reviews_record FOREIGN KEY(record_id) REFERENCES experiment_records(id),
  CONSTRAINT fk_reviews_revision FOREIGN KEY(revision_id) REFERENCES record_revisions(id),
  CONSTRAINT fk_reviews_reviewer FOREIGN KEY(reviewer_id) REFERENCES users(id)
);
CREATE INDEX idx_reviews_reviewer_status ON reviews(reviewer_id,status,assigned_at);
CREATE INDEX idx_reviews_record_status ON reviews(record_id,status);

CREATE TABLE revision_attachments (
  revision_id CHAR(36) NOT NULL,
  attachment_id CHAR(36) NOT NULL,
  sort_order INT NOT NULL,
  PRIMARY KEY(revision_id,attachment_id),
  CONSTRAINT fk_revision_attachments_revision FOREIGN KEY(revision_id) REFERENCES record_revisions(id),
  CONSTRAINT fk_revision_attachments_attachment FOREIGN KEY(attachment_id) REFERENCES attachments(id)
);
CREATE INDEX idx_revision_attachments_order ON revision_attachments(revision_id,sort_order);

ALTER TABLE experiment_records ADD CONSTRAINT fk_records_current_review FOREIGN KEY(current_review_id) REFERENCES reviews(id);
ALTER TABLE experiment_records ADD CONSTRAINT fk_records_final_revision FOREIGN KEY(final_revision_id) REFERENCES record_revisions(id);
