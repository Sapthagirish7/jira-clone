-- V3: Add atomic issue number counter to projects table
-- Replaces COUNT(*)-based key generation which has a TOCTOU race condition
-- under concurrent issue creation (multiple VUs hitting POST /issues simultaneously).

ALTER TABLE projects ADD COLUMN next_issue_number INT NOT NULL DEFAULT 0;

-- Seed existing projects with the correct current count
UPDATE projects p
SET next_issue_number = (SELECT COUNT(*) FROM issues i WHERE i.project_id = p.id);
