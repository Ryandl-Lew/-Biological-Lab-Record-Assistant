CREATE TABLE agent_chat_references (
  id CHAR(36) NOT NULL PRIMARY KEY,
  project_id CHAR(36) NOT NULL,
  uploaded_by CHAR(36) NOT NULL,
  storage_key CHAR(36) NOT NULL,
  original_filename VARCHAR(255) NOT NULL,
  content_type VARCHAR(120) NOT NULL,
  size_bytes BIGINT NOT NULL,
  peek_json TEXT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_agent_chat_ref_project FOREIGN KEY (project_id) REFERENCES projects(id),
  CONSTRAINT fk_agent_chat_ref_user FOREIGN KEY (uploaded_by) REFERENCES users(id)
);

CREATE INDEX idx_agent_chat_ref_project ON agent_chat_references(project_id);
CREATE INDEX idx_agent_chat_ref_expires ON agent_chat_references(expires_at);
