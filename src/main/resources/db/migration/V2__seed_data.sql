-- V2: Demo seed data
-- Creates a sample project "DEMO" with statuses, transitions, sprint, and issues.
-- Run 'docker-compose up' and this executes automatically.

-- Demo project
INSERT INTO projects (id, key, name, description, owner_id) VALUES
    ('10000000-0000-0000-0000-000000000001',
     'DEMO', 'Demo Project', 'Seeded project for testing', '00000000-0000-0000-0000-000000000001');

-- Add admin as project member
INSERT INTO project_members (project_id, user_id, role) VALUES
    ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'ADMIN');

-- Workflow statuses (ordered by position)
INSERT INTO workflow_statuses (id, project_id, name, category, position, is_default) VALUES
    ('20000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000001', 'To Do',       'TODO',        0, true),
    ('20000000-0000-0000-0000-000000000002',
     '10000000-0000-0000-0000-000000000001', 'In Progress',  'IN_PROGRESS', 1, false),
    ('20000000-0000-0000-0000-000000000003',
     '10000000-0000-0000-0000-000000000001', 'In Review',    'IN_PROGRESS', 2, false),
    ('20000000-0000-0000-0000-000000000004',
     '10000000-0000-0000-0000-000000000001', 'Done',         'DONE',        3, false);

-- Allowed transitions (directed graph — enforces workflow rules)
-- "To Do" -> "In Progress" only; cannot jump to "Done" directly (Scenario 3)
INSERT INTO workflow_transitions (id, project_id, from_status_id, to_status_id) VALUES
    (gen_random_uuid(), '10000000-0000-0000-0000-000000000001',
     '20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000002'),
    (gen_random_uuid(), '10000000-0000-0000-0000-000000000001',
     '20000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000003'),
    (gen_random_uuid(), '10000000-0000-0000-0000-000000000001',
     '20000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000004'),
    -- Allow moving back (re-open)
    (gen_random_uuid(), '10000000-0000-0000-0000-000000000001',
     '20000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001'),
    (gen_random_uuid(), '10000000-0000-0000-0000-000000000001',
     '20000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000002');

-- Sample sprint
INSERT INTO sprints (id, project_id, name, goal, status, start_date, end_date) VALUES
    ('30000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000001',
     'Sprint 1', 'Ship the auth flow', 'ACTIVE',
     CURRENT_DATE, CURRENT_DATE + 14);

-- Sample issues
INSERT INTO issues (id, issue_key, project_id, issue_type, title, status_id, priority, reporter_id, story_points, version) VALUES
    ('40000000-0000-0000-0000-000000000001',
     'DEMO-1', '10000000-0000-0000-0000-000000000001',
     'STORY', 'User can log in via OAuth',
     '20000000-0000-0000-0000-000000000002', 'HIGH',
     '00000000-0000-0000-0000-000000000001', 5, 0),
    ('40000000-0000-0000-0000-000000000002',
     'DEMO-2', '10000000-0000-0000-0000-000000000001',
     'BUG', 'Fix null pointer on empty password',
     '20000000-0000-0000-0000-000000000001', 'CRITICAL',
     '00000000-0000-0000-0000-000000000001', 2, 0),
    ('40000000-0000-0000-0000-000000000003',
     'DEMO-3', '10000000-0000-0000-0000-000000000001',
     'TASK', 'Write API documentation',
     '20000000-0000-0000-0000-000000000003', 'LOW',
     '00000000-0000-0000-0000-000000000001', 1, 0);

-- Assign sprint 1 to two issues
UPDATE issues SET sprint_id = '30000000-0000-0000-0000-000000000001'
WHERE id IN ('40000000-0000-0000-0000-000000000001', '40000000-0000-0000-0000-000000000003');
-- DEMO-2 stays in backlog (sprint_id = NULL)
