CREATE TABLE agent_chat_sessions (
    id CHAR(36) NOT NULL,
    project_id CHAR(36),
    record_id CHAR(36),
    user_id CHAR(36) NOT NULL,
    title VARCHAR(200) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE agent_chat_messages (
    id CHAR(36) NOT NULL,
    session_id CHAR(36) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    FOREIGN KEY (session_id) REFERENCES agent_chat_sessions(id)
);

CREATE INDEX idx_chat_sessions_user ON agent_chat_sessions(user_id, updated_at DESC);
CREATE INDEX idx_chat_messages_session ON agent_chat_messages(session_id, created_at);
