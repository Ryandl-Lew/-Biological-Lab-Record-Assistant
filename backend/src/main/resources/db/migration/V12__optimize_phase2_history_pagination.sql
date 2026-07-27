CREATE INDEX idx_restore_record_time_id
  ON record_restore_operations(record_id, restored_at DESC, id DESC);

CREATE INDEX idx_agent_artifacts_project_time_id
  ON agent_artifacts(project_id, created_at DESC, id DESC);

CREATE INDEX idx_agent_artifacts_record_time_id
  ON agent_artifacts(record_id, created_at DESC, id DESC);
