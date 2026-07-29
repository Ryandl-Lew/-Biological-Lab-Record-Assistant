CREATE TABLE prompt_versions (
  id CHAR(36) NOT NULL PRIMARY KEY,
  prompt_name VARCHAR(100) NOT NULL,
  version_no INT NOT NULL,
  template_text TEXT NOT NULL,
  output_schema_json TEXT NOT NULL,
  tool_policy_json TEXT NOT NULL,
  content_hash CHAR(64) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT FALSE,
  active_name_key VARCHAR(100) NULL,
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT uk_prompt_name_version UNIQUE(prompt_name,version_no),
  CONSTRAINT uk_prompt_active_name UNIQUE(active_name_key)
);

CREATE TABLE agent_runs (
  id CHAR(36) NOT NULL PRIMARY KEY,
  artifact_kind VARCHAR(30) NOT NULL,
  subject_type VARCHAR(20) NOT NULL,
  subject_id CHAR(36) NOT NULL,
  project_id CHAR(36) NOT NULL,
  record_id CHAR(36) NULL,
  requested_by CHAR(36) NOT NULL,
  trigger_type VARCHAR(20) NOT NULL,
  status VARCHAR(30) NOT NULL,
  provider VARCHAR(60) NOT NULL,
  model VARCHAR(120) NOT NULL,
  prompt_version_id CHAR(36) NOT NULL,
  parent_run_id CHAR(36) NULL,
  idempotency_key VARCHAR(160) NOT NULL,
  request_json TEXT NOT NULL,
  payload_hash CHAR(64) NOT NULL,
  input_cursor_json TEXT NOT NULL,
  limits_json TEXT NOT NULL,
  step_count INT NOT NULL DEFAULT 0,
  tool_call_count INT NOT NULL DEFAULT 0,
  input_tokens BIGINT NOT NULL DEFAULT 0,
  output_tokens BIGINT NOT NULL DEFAULT 0,
  error_code VARCHAR(80) NULL,
  error_message VARCHAR(1000) NULL,
  cancel_requested_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL,
  started_at TIMESTAMP(6) NULL,
  finished_at TIMESTAMP(6) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uk_agent_run_request_key UNIQUE(requested_by,idempotency_key),
  CONSTRAINT fk_agent_run_project FOREIGN KEY(project_id) REFERENCES projects(id),
  CONSTRAINT fk_agent_run_record FOREIGN KEY(record_id) REFERENCES experiment_records(id),
  CONSTRAINT fk_agent_run_requester FOREIGN KEY(requested_by) REFERENCES users(id),
  CONSTRAINT fk_agent_run_prompt FOREIGN KEY(prompt_version_id) REFERENCES prompt_versions(id),
  CONSTRAINT fk_agent_run_parent FOREIGN KEY(parent_run_id) REFERENCES agent_runs(id)
);
CREATE INDEX idx_agent_runs_queue ON agent_runs(status,created_at);
CREATE INDEX idx_agent_runs_project_time ON agent_runs(project_id,created_at DESC);
CREATE INDEX idx_agent_runs_record_time ON agent_runs(record_id,created_at DESC);

CREATE TABLE agent_steps (
  id CHAR(36) NOT NULL PRIMARY KEY,
  run_id CHAR(36) NOT NULL,
  step_no INT NOT NULL,
  step_type VARCHAR(30) NOT NULL,
  tool_name VARCHAR(100) NULL,
  request_json TEXT NULL,
  response_json TEXT NULL,
  content_hash CHAR(64) NOT NULL,
  latency_ms BIGINT NOT NULL DEFAULT 0,
  input_tokens BIGINT NOT NULL DEFAULT 0,
  output_tokens BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT uk_agent_step_no UNIQUE(run_id,step_no),
  CONSTRAINT fk_agent_step_run FOREIGN KEY(run_id) REFERENCES agent_runs(id)
);

CREATE TABLE agent_artifacts (
  id CHAR(36) NOT NULL PRIMARY KEY,
  run_id CHAR(36) NOT NULL,
  artifact_kind VARCHAR(30) NOT NULL,
  project_id CHAR(36) NOT NULL,
  record_id CHAR(36) NULL,
  content_json TEXT NOT NULL,
  evidence_json TEXT NOT NULL,
  content_hash CHAR(64) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT uk_agent_artifact_run UNIQUE(run_id),
  CONSTRAINT fk_agent_artifact_run FOREIGN KEY(run_id) REFERENCES agent_runs(id),
  CONSTRAINT fk_agent_artifact_project FOREIGN KEY(project_id) REFERENCES projects(id),
  CONSTRAINT fk_agent_artifact_record FOREIGN KEY(record_id) REFERENCES experiment_records(id)
);
CREATE INDEX idx_agent_artifacts_project_kind ON agent_artifacts(project_id,artifact_kind,created_at DESC);
CREATE INDEX idx_agent_artifacts_record_kind ON agent_artifacts(record_id,artifact_kind,created_at DESC);
