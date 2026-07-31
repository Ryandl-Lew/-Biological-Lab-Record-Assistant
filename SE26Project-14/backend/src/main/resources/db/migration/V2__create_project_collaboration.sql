CREATE TABLE projects (
  id CHAR(36) NOT NULL PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(2000) NULL,
  status VARCHAR(20) NOT NULL,
  owner_id CHAR(36) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  archived_at TIMESTAMP(6) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_projects_owner FOREIGN KEY (owner_id) REFERENCES users(id)
);
CREATE INDEX idx_projects_owner_status ON projects(owner_id, status);

CREATE TABLE project_members (
  project_id CHAR(36) NOT NULL,
  user_id CHAR(36) NOT NULL,
  role VARCHAR(20) NOT NULL,
  joined_at TIMESTAMP(6) NOT NULL,
  last_active_at TIMESTAMP(6) NULL,
  PRIMARY KEY(project_id, user_id),
  CONSTRAINT fk_members_project FOREIGN KEY(project_id) REFERENCES projects(id),
  CONSTRAINT fk_members_user FOREIGN KEY(user_id) REFERENCES users(id)
);
CREATE INDEX idx_members_user ON project_members(user_id, project_id);

CREATE TABLE project_invitations (
  id CHAR(36) NOT NULL PRIMARY KEY,
  project_id CHAR(36) NOT NULL,
  inviter_id CHAR(36) NOT NULL,
  invitee_user_id CHAR(36) NOT NULL,
  invitee_email_snapshot VARCHAR(255) NOT NULL,
  status VARCHAR(20) NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  responded_at TIMESTAMP(6) NULL,
  pending_key VARCHAR(100) NULL,
  CONSTRAINT uk_invitation_pending UNIQUE(pending_key),
  CONSTRAINT fk_invitations_project FOREIGN KEY(project_id) REFERENCES projects(id),
  CONSTRAINT fk_invitations_inviter FOREIGN KEY(inviter_id) REFERENCES users(id),
  CONSTRAINT fk_invitations_invitee FOREIGN KEY(invitee_user_id) REFERENCES users(id)
);
CREATE INDEX idx_invitations_invitee_status ON project_invitations(invitee_user_id, status, expires_at);

CREATE TABLE notifications (
  id CHAR(36) NOT NULL PRIMARY KEY,
  recipient_id CHAR(36) NOT NULL,
  type VARCHAR(50) NOT NULL,
  title VARCHAR(200) NOT NULL,
  body VARCHAR(1000) NOT NULL,
  payload_json TEXT NOT NULL,
  dedup_key VARCHAR(200) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  read_at TIMESTAMP(6) NULL,
  CONSTRAINT uk_notifications_dedup UNIQUE(dedup_key),
  CONSTRAINT fk_notifications_recipient FOREIGN KEY(recipient_id) REFERENCES users(id)
);
CREATE INDEX idx_notifications_recipient_created ON notifications(recipient_id, created_at);
CREATE INDEX idx_notifications_recipient_read ON notifications(recipient_id, read_at);

CREATE TABLE audit_events (
  id CHAR(36) NOT NULL PRIMARY KEY,
  actor_id CHAR(36) NULL,
  project_id CHAR(36) NULL,
  record_id CHAR(36) NULL,
  event_type VARCHAR(80) NOT NULL,
  target_type VARCHAR(50) NOT NULL,
  target_id CHAR(36) NOT NULL,
  metadata_json TEXT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_audit_actor FOREIGN KEY(actor_id) REFERENCES users(id),
  CONSTRAINT fk_audit_project FOREIGN KEY(project_id) REFERENCES projects(id)
);
CREATE INDEX idx_audit_project_created ON audit_events(project_id, created_at);

