ALTER TABLE personal_todo ADD COLUMN client_request_id VARCHAR(64) DEFAULT NULL;
ALTER TABLE personal_todo ADD COLUMN version INT NOT NULL DEFAULT 0;
CREATE UNIQUE INDEX uk_personal_todo_client_request ON personal_todo (owner_id, client_request_id);
