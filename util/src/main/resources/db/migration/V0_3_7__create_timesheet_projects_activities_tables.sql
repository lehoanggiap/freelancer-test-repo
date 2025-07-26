-- Create timesheet management tables: projects, activities, and timesheet_entries
-- Establishes normalized schema for time tracking with proper relationships
-- Includes optimized indexes and default data seeding for existing tenants

-- Create projects table with optimized column ordering (column tetris)
CREATE TABLE IF NOT EXISTS projects (
    id                INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id         INTEGER      NOT NULL,
    name              VARCHAR(128) NOT NULL,
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,
    color             CHAR(7),     -- Hex color code
    description       TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_projects_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT unique_projects_tenant_name UNIQUE (tenant_id, name)
);

-- Create activities table with optimized column ordering
CREATE TABLE IF NOT EXISTS activities (
    id                INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id         INTEGER      NOT NULL,
    project_id        INTEGER,     -- NULL means global activity
    name              VARCHAR(128) NOT NULL,
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,
    is_billable       BOOLEAN      NOT NULL DEFAULT TRUE,
    hourly_rate       DOUBLE PRECISION, -- Changed from NUMERIC to match Double entity type
    description       TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_activities_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT fk_activities_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
);

-- Create timesheet_entries table with optimized column ordering
CREATE TABLE IF NOT EXISTS timesheet_entries (
    id                INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           INTEGER      NOT NULL,
    tenant_id         INTEGER      NOT NULL,
    project_id        INTEGER,
    activity_id       INTEGER,
    entry_date        DATE         NOT NULL,
    hours             DOUBLE PRECISION NOT NULL, -- Changed from NUMERIC to match Double entity type
    status            VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    description       TEXT,
    notes             TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_timesheet_entries_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_timesheet_entries_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT fk_timesheet_entries_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE SET NULL,
    CONSTRAINT fk_timesheet_entries_activity FOREIGN KEY (activity_id) REFERENCES activities (id) ON DELETE SET NULL,
    CONSTRAINT check_positive_hours CHECK (hours >= 0)
);

-- Create optimized indexes for performance
CREATE INDEX IF NOT EXISTS idx_projects_tenant_active ON projects (tenant_id, is_active) WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS idx_activities_tenant_active ON activities (tenant_id, is_active) WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS idx_activities_project ON activities (project_id) WHERE project_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_timesheet_entries_user_date ON timesheet_entries (user_id, entry_date);
CREATE INDEX IF NOT EXISTS idx_timesheet_entries_tenant_date ON timesheet_entries (tenant_id, entry_date);
CREATE INDEX IF NOT EXISTS idx_timesheet_entries_project ON timesheet_entries (project_id) WHERE project_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_timesheet_entries_activity ON timesheet_entries (activity_id) WHERE activity_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_timesheet_entries_status ON timesheet_entries (status);

-- Insert default projects for existing tenants
INSERT INTO projects (tenant_id, name, description, color, is_active)
SELECT DISTINCT 
    t.id as tenant_id,
    'General' as name,
    'Default general project' as description,
    '#6B7280' as color,
    TRUE as is_active
FROM tenants t
WHERE NOT EXISTS (
    SELECT 1 FROM projects p WHERE p.tenant_id = t.id
);

-- Insert default activities for existing tenants
INSERT INTO activities (tenant_id, name, description, is_billable, is_active)
SELECT DISTINCT 
    t.id as tenant_id,
    activity_name,
    'Default ' || activity_name || ' activity',
    is_billable,
    TRUE
FROM tenants t
CROSS JOIN (
    VALUES 
        ('Development', TRUE),
        ('Meeting', TRUE),
        ('Code Review', TRUE),
        ('Documentation', TRUE),
        ('Testing', TRUE),
        ('Research', FALSE),
        ('Training', FALSE)
) AS activities(activity_name, is_billable)
WHERE NOT EXISTS (
    SELECT 1 FROM activities a WHERE a.tenant_id = t.id
);
