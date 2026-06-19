-- ─────────────────────────────────────────────────────────────────────────────
-- V14: Workflow Management — execution (a workflow template assigned to a student,
--      its current stage, and the per-stage history used for SLA + analytics).
-- ─────────────────────────────────────────────────────────────────────────────

-- ── Student workflow instances (PRD §4 "Workflow Instance", §14) ───────────────

CREATE TABLE student_workflows (
    id                       UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                UUID         NOT NULL,
    student_id               UUID         NOT NULL,
    workflow_template_id     UUID         NOT NULL,
    current_stage_id         UUID,

    status                   VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    started_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at             TIMESTAMPTZ,
    current_stage_entered_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sla_breached             BOOLEAN      NOT NULL DEFAULT FALSE,

    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by               VARCHAR(255),
    updated_by               VARCHAR(255),

    CONSTRAINT pk_student_workflows           PRIMARY KEY (id),
    CONSTRAINT fk_student_workflows_tenant    FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT fk_student_workflows_student   FOREIGN KEY (student_id)
        REFERENCES students (id) ON DELETE CASCADE,
    CONSTRAINT fk_student_workflows_template  FOREIGN KEY (workflow_template_id)
        REFERENCES workflow_templates (id),
    CONSTRAINT fk_student_workflows_stage     FOREIGN KEY (current_stage_id)
        REFERENCES workflow_stages (id) ON DELETE SET NULL,
    CONSTRAINT chk_student_workflows_status   CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_student_workflows_tenant_id ON student_workflows (tenant_id);
CREATE INDEX idx_student_workflows_student   ON student_workflows (student_id);
CREATE INDEX idx_student_workflows_stage     ON student_workflows (current_stage_id);
CREATE INDEX idx_student_workflows_status    ON student_workflows (tenant_id, status);

-- At most one ACTIVE workflow per student.
CREATE UNIQUE INDEX uq_student_workflows_active
    ON student_workflows (student_id) WHERE status = 'ACTIVE';

-- ── Stage history (one row per stage occupancy) ───────────────────────────────

CREATE TABLE workflow_stage_history (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id            UUID         NOT NULL,
    student_workflow_id  UUID         NOT NULL,
    stage_id             UUID         NOT NULL,

    entered_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    exited_at            TIMESTAMPTZ,
    moved_by_user_id     UUID,
    transition_id        UUID,
    notes                VARCHAR(1000),

    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by           VARCHAR(255),
    updated_by           VARCHAR(255),

    CONSTRAINT pk_workflow_stage_history          PRIMARY KEY (id),
    CONSTRAINT fk_wsh_tenant   FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT fk_wsh_instance FOREIGN KEY (student_workflow_id)
        REFERENCES student_workflows (id) ON DELETE CASCADE,
    CONSTRAINT fk_wsh_stage    FOREIGN KEY (stage_id) REFERENCES workflow_stages (id)
);

CREATE INDEX idx_wsh_tenant_id ON workflow_stage_history (tenant_id);
CREATE INDEX idx_wsh_instance  ON workflow_stage_history (student_workflow_id);
CREATE INDEX idx_wsh_stage     ON workflow_stage_history (stage_id);

COMMENT ON TABLE  student_workflows                          IS 'A workflow template assigned to a student; current_stage is the process source of truth';
COMMENT ON COLUMN student_workflows.current_stage_entered_at IS 'When the student entered current_stage; basis for on-read SLA calculation';
COMMENT ON COLUMN student_workflows.sla_breached             IS 'Maintained by the SLA job; complements on-read computation';
COMMENT ON TABLE  workflow_stage_history                     IS 'Per-stage occupancy log for SLA tracking and analytics';
