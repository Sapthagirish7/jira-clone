-- V1: Full schema for Jira-like Project Management Platform

-- ─────────────────────────────────────────────
-- USERS
-- ─────────────────────────────────────────────
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(100) NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    display_name  VARCHAR(200) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ─────────────────────────────────────────────
-- PROJECTS
-- ─────────────────────────────────────────────
CREATE TABLE projects (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key         VARCHAR(20)  NOT NULL UNIQUE,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    owner_id    UUID NOT NULL REFERENCES users(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ─────────────────────────────────────────────
-- PROJECT MEMBERS + RBAC
-- ─────────────────────────────────────────────
CREATE TABLE project_members (
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES users(id),
    role       VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN','PROJECT_LEAD','MEMBER','VIEWER')),
    joined_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (project_id, user_id)
);

-- ─────────────────────────────────────────────
-- WORKFLOW STATUSES  (configurable per project)
-- ─────────────────────────────────────────────
CREATE TABLE workflow_statuses (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    category   VARCHAR(20)  NOT NULL CHECK (category IN ('TODO','IN_PROGRESS','DONE')),
    position   INT NOT NULL DEFAULT 0,
    is_default BOOLEAN NOT NULL DEFAULT false,
    UNIQUE (project_id, name)
);

-- Allowed transitions between statuses (directed graph)
CREATE TABLE workflow_transitions (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id     UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    from_status_id UUID NOT NULL REFERENCES workflow_statuses(id),
    to_status_id   UUID NOT NULL REFERENCES workflow_statuses(id),
    requires_role  VARCHAR(20),
    UNIQUE (from_status_id, to_status_id)
);

-- Automatic actions fired when a transition is used
CREATE TABLE transition_actions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transition_id    UUID NOT NULL REFERENCES workflow_transitions(id) ON DELETE CASCADE,
    action_type      VARCHAR(50) NOT NULL,
    action_payload   JSONB
);

-- ─────────────────────────────────────────────
-- SPRINTS
-- ─────────────────────────────────────────────
CREATE TABLE sprints (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name       VARCHAR(255) NOT NULL,
    goal       TEXT,
    status     VARCHAR(20) NOT NULL DEFAULT 'PLANNED'
                   CHECK (status IN ('PLANNED','ACTIVE','COMPLETED')),
    start_date DATE,
    end_date   DATE,
    velocity   INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ─────────────────────────────────────────────
-- ISSUES
-- ─────────────────────────────────────────────
CREATE TABLE issues (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_key     VARCHAR(30)  NOT NULL UNIQUE,
    project_id    UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    issue_type    VARCHAR(20)  NOT NULL CHECK (issue_type IN ('EPIC','STORY','TASK','BUG','SUB_TASK')),
    title         VARCHAR(500) NOT NULL,
    description   TEXT,
    status_id     UUID NOT NULL REFERENCES workflow_statuses(id),
    priority      VARCHAR(20)  NOT NULL DEFAULT 'MEDIUM'
                      CHECK (priority IN ('CRITICAL','HIGH','MEDIUM','LOW')),
    assignee_id   UUID REFERENCES users(id),
    reporter_id   UUID NOT NULL REFERENCES users(id),
    parent_id     UUID REFERENCES issues(id),
    sprint_id     UUID REFERENCES sprints(id),
    story_points  INT,
    labels        TEXT[],
    version       INT NOT NULL DEFAULT 0,
    search_vector TSVECTOR,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Custom field definitions per project
CREATE TABLE project_custom_fields (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    field_type VARCHAR(20)  NOT NULL CHECK (field_type IN ('TEXT','NUMBER','DROPDOWN','DATE')),
    options    JSONB,
    UNIQUE (project_id, name)
);

-- Custom field values per issue
CREATE TABLE issue_custom_fields (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_id   UUID NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    field_id   UUID NOT NULL REFERENCES project_custom_fields(id),
    value_text TEXT,
    value_num  NUMERIC,
    value_date DATE,
    UNIQUE (issue_id, field_id)
);

-- ─────────────────────────────────────────────
-- WATCHERS
-- ─────────────────────────────────────────────
CREATE TABLE issue_watchers (
    issue_id UUID NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    user_id  UUID NOT NULL REFERENCES users(id),
    PRIMARY KEY (issue_id, user_id)
);

-- ─────────────────────────────────────────────
-- COMMENTS  (threaded)
-- ─────────────────────────────────────────────
CREATE TABLE comments (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_id          UUID NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    author_id         UUID NOT NULL REFERENCES users(id),
    parent_comment_id UUID REFERENCES comments(id),
    body              TEXT NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE comment_mentions (
    comment_id UUID NOT NULL REFERENCES comments(id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES users(id),
    PRIMARY KEY (comment_id, user_id)
);

-- ─────────────────────────────────────────────
-- ACTIVITY LOG  (immutable audit trail)
-- ─────────────────────────────────────────────
CREATE TABLE activity_log (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id     UUID NOT NULL REFERENCES projects(id),
    issue_id       UUID REFERENCES issues(id),
    actor_id       UUID NOT NULL REFERENCES users(id),
    event_type     VARCHAR(50) NOT NULL,
    old_value      JSONB,
    new_value      JSONB,
    correlation_id VARCHAR(64),
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ─────────────────────────────────────────────
-- NOTIFICATIONS
-- ─────────────────────────────────────────────
CREATE TABLE notifications (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id UUID NOT NULL REFERENCES users(id),
    issue_id     UUID REFERENCES issues(id),
    type         VARCHAR(50) NOT NULL,
    message      TEXT NOT NULL,
    read         BOOLEAN NOT NULL DEFAULT false,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ─────────────────────────────────────────────
-- INDEXES
-- ─────────────────────────────────────────────
CREATE INDEX idx_issues_project_status  ON issues(project_id, status_id);
CREATE INDEX idx_issues_sprint          ON issues(sprint_id) WHERE sprint_id IS NOT NULL;
CREATE INDEX idx_issues_backlog         ON issues(project_id) WHERE sprint_id IS NULL;
CREATE INDEX idx_issues_parent          ON issues(parent_id) WHERE parent_id IS NOT NULL;
CREATE INDEX idx_issues_assignee        ON issues(assignee_id) WHERE assignee_id IS NOT NULL;
CREATE INDEX idx_issues_search          ON issues USING GIN(search_vector);
CREATE INDEX idx_activity_project_time  ON activity_log(project_id, occurred_at DESC);
CREATE INDEX idx_activity_issue         ON activity_log(issue_id, occurred_at DESC);
CREATE INDEX idx_notifications_unread   ON notifications(recipient_id, read, created_at DESC);

-- ─────────────────────────────────────────────
-- FULL-TEXT SEARCH TRIGGER
-- Keeps search_vector in sync with title + description changes.
-- GIN index on tsvector = fast @@ queries without Elasticsearch.
-- ─────────────────────────────────────────────
CREATE OR REPLACE FUNCTION update_issue_search_vector()
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('english', coalesce(NEW.title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(NEW.description, '')), 'B');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_issues_search_vector
    BEFORE INSERT OR UPDATE OF title, description
    ON issues
    FOR EACH ROW EXECUTE FUNCTION update_issue_search_vector();

-- ─────────────────────────────────────────────
-- SEED: admin user  (password = "admin123", bcrypt)
-- ─────────────────────────────────────────────
INSERT INTO users (id, username, email, display_name, password_hash) VALUES
    ('00000000-0000-0000-0000-000000000001',
     'admin', 'admin@jira.local', 'System Admin',
     '$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy');
