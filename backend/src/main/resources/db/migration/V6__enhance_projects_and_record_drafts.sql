ALTER TABLE projects ADD detailed_description TEXT NULL;

ALTER TABLE experiment_records ADD provisional BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_records_provisional_creator ON experiment_records(provisional, creator_id, updated_at);
