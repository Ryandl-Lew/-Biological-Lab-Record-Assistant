CREATE INDEX idx_records_search_scope ON experiment_records(project_id,deleted_at,updated_at);
CREATE INDEX idx_attachments_search_scope ON attachments(record_id,deleted_at,created_at);
CREATE INDEX idx_reviews_pending_tasks ON reviews(reviewer_id,status,record_id,assigned_at);
CREATE INDEX idx_audit_project_type_actor_time ON audit_events(project_id,event_type,actor_id,created_at);
