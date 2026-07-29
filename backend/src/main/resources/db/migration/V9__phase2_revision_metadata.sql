ALTER TABLE record_revisions
  ADD COLUMN snapshot_schema_version INT NOT NULL DEFAULT 1;

CREATE INDEX idx_revisions_record_no_desc
  ON record_revisions(record_id, revision_no DESC);
