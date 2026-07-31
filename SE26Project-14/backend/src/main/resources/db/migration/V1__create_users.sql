CREATE TABLE users (
  id CHAR(36) NOT NULL PRIMARY KEY,
  display_name VARCHAR(50) NOT NULL,
  email_normalized VARCHAR(255) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  avatar_storage_key VARCHAR(255) NULL,
  avatar_mime_type VARCHAR(100) NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uk_users_email UNIQUE (email_normalized)
);

