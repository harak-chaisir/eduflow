-- ─────────────────────────────────────────────────────────────────────────────
-- V15: Task domain — work items generated from workflow stages (PRD §4 "Task",
--      §7.7). Each task is owned by a role (and optionally a specific user) and
--      tracked through a PENDING → IN_PROGRESS → COMPLETED | CANCELLED lifecycle.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE tasks (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id            UUID         NOT NULL,
    student_id           UUID         NOT NULL,
    student_workflow_id  UUID,
    stage_id             UUID,

    title                VARCHAR(200) NOT NULL,
    description          VARCHAR(1000),
    assigned_role        VARCHAR(60),
    assigned_user_id     UUID,
    status               VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    priority             VARCHAR(20)  NOT NULL DEFAULT 'MEDIUM',
    due_at               TIMESTAMPTZ,
    completed_at         TIMESTAMPTZ,
    completed_by         VARCHAR(255),

    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by           VARCHAR(255),
    updated_by           VARCHAR(255),

    CONSTRAINT pk_tasks            PRIMARY KEY (id),
    CONSTRAINT fk_tasks_tenant     FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT fk_tasks_student    FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE,
    CONSTRAINT fk_tasks_instance   FOREIGN KEY (student_workflow_id)
        REFERENCES student_workflows (id) ON DELETE CASCADE,
    CONSTRAINT fk_tasks_stage      FOREIGN KEY (stage_id) REFERENCES workflow_stages (id) ON DELETE SET NULL,
    CONSTRAINT fk_tasks_assignee   FOREIGN KEY (assigned_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_tasks_status    CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_tasks_priority  CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH'))
);

CREATE INDEX idx_tasks_tenant_id   ON tasks (tenant_id);
CREATE INDEX idx_tasks_student     ON tasks (student_id);
CREATE INDEX idx_tasks_assignee    ON tasks (assigned_user_id);
CREATE INDEX idx_tasks_role        ON tasks (tenant_id, assigned_role);
CREATE INDEX idx_tasks_status      ON tasks (tenant_id, status);

COMMENT ON TABLE  tasks               IS 'Work items generated from workflow stages (PRD §7.7)';
COMMENT ON COLUMN tasks.assigned_role IS 'Role responsible, e.g. ROLE_DOC_OFFICER; mirrors the stage owner role';
COMMENT ON COLUMN tasks.status        IS 'PENDING | IN_PROGRESS | COMPLETED | CANCELLED';
