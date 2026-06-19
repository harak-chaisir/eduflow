-- ─────────────────────────────────────────────────────────────────────────────
-- V13: Workflow Management — definition tables (templates, stages, transitions,
--      and per-stage required documents).
--
--      Configurable per tenant: a tenant admin builds a workflow template made of
--      ordered stages connected by typed transitions. Execution (student
--      instances, tasks) is introduced in later migrations.
-- ─────────────────────────────────────────────────────────────────────────────

-- ── Workflow templates ───────────────────────────────────────────────────────

CREATE TABLE workflow_templates (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id     UUID         NOT NULL,

    name          VARCHAR(150) NOT NULL,
    description   VARCHAR(1000),
    country       VARCHAR(100),
    version       INTEGER      NOT NULL DEFAULT 1,

    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    is_default    BOOLEAN      NOT NULL DEFAULT FALSE,
    is_archived   BOOLEAN      NOT NULL DEFAULT FALSE,

    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    VARCHAR(255),
    updated_by    VARCHAR(255),

    CONSTRAINT pk_workflow_templates              PRIMARY KEY (id),
    CONSTRAINT uq_workflow_templates_name_version UNIQUE (tenant_id, name, version),
    CONSTRAINT fk_workflow_templates_tenant       FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE CASCADE
);

CREATE INDEX idx_workflow_templates_tenant_id ON workflow_templates (tenant_id);
CREATE INDEX idx_workflow_templates_active    ON workflow_templates (tenant_id, is_active);

-- ── Workflow stages ──────────────────────────────────────────────────────────

CREATE TABLE workflow_stages (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id            UUID         NOT NULL,
    workflow_template_id UUID         NOT NULL,

    name                 VARCHAR(150) NOT NULL,
    code                 VARCHAR(60)  NOT NULL,
    display_order        INTEGER      NOT NULL,
    description          VARCHAR(1000),
    color                VARCHAR(20),
    is_active            BOOLEAN      NOT NULL DEFAULT TRUE,
    sla_days             INTEGER,
    stage_type           VARCHAR(40)  NOT NULL DEFAULT 'NORMAL',
    owner_role           VARCHAR(60),

    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by           VARCHAR(255),
    updated_by           VARCHAR(255),

    CONSTRAINT pk_workflow_stages           PRIMARY KEY (id),
    CONSTRAINT uq_workflow_stages_code      UNIQUE (workflow_template_id, code),
    CONSTRAINT fk_workflow_stages_template  FOREIGN KEY (workflow_template_id)
        REFERENCES workflow_templates (id) ON DELETE CASCADE,
    CONSTRAINT fk_workflow_stages_tenant    FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT chk_workflow_stages_type     CHECK (stage_type IN (
        'NORMAL', 'DOCUMENT_STAGE', 'APPLICATION_STAGE', 'VISA_STAGE',
        'DECISION_STAGE', 'FINAL_STAGE'
    )),
    CONSTRAINT chk_workflow_stages_sla      CHECK (sla_days IS NULL OR sla_days > 0)
);

CREATE INDEX idx_workflow_stages_tenant_id ON workflow_stages (tenant_id);
CREATE INDEX idx_workflow_stages_template  ON workflow_stages (workflow_template_id, display_order);

-- ── Per-stage required documents (element collection of DocumentType) ──────────

CREATE TABLE workflow_stage_required_documents (
    stage_id      UUID        NOT NULL,
    document_type VARCHAR(60) NOT NULL,

    CONSTRAINT pk_workflow_stage_required_documents
        PRIMARY KEY (stage_id, document_type),
    CONSTRAINT fk_wsrd_stage FOREIGN KEY (stage_id)
        REFERENCES workflow_stages (id) ON DELETE CASCADE
);

CREATE INDEX idx_wsrd_stage_id ON workflow_stage_required_documents (stage_id);

-- ── Workflow transitions (directed edges between stages) ───────────────────────

CREATE TABLE workflow_transitions (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id            UUID         NOT NULL,
    workflow_template_id UUID         NOT NULL,

    from_stage_id        UUID         NOT NULL,
    to_stage_id          UUID         NOT NULL,
    transition_type      VARCHAR(40)  NOT NULL DEFAULT 'FORWARD',
    label                VARCHAR(150),
    requires_approval    BOOLEAN      NOT NULL DEFAULT FALSE,

    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by           VARCHAR(255),
    updated_by           VARCHAR(255),

    CONSTRAINT pk_workflow_transitions          PRIMARY KEY (id),
    CONSTRAINT uq_workflow_transitions_pair     UNIQUE (workflow_template_id, from_stage_id, to_stage_id),
    CONSTRAINT fk_workflow_transitions_template FOREIGN KEY (workflow_template_id)
        REFERENCES workflow_templates (id) ON DELETE CASCADE,
    CONSTRAINT fk_workflow_transitions_tenant   FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT fk_workflow_transitions_from     FOREIGN KEY (from_stage_id)
        REFERENCES workflow_stages (id) ON DELETE CASCADE,
    CONSTRAINT fk_workflow_transitions_to       FOREIGN KEY (to_stage_id)
        REFERENCES workflow_stages (id) ON DELETE CASCADE,
    CONSTRAINT chk_workflow_transitions_type    CHECK (transition_type IN (
        'FORWARD', 'BACKWARD', 'CONDITIONAL', 'MANUAL_APPROVAL'
    )),
    CONSTRAINT chk_workflow_transitions_no_self CHECK (from_stage_id <> to_stage_id)
);

CREATE INDEX idx_workflow_transitions_tenant_id ON workflow_transitions (tenant_id);
CREATE INDEX idx_workflow_transitions_template  ON workflow_transitions (workflow_template_id);
CREATE INDEX idx_workflow_transitions_from      ON workflow_transitions (from_stage_id);

-- ── Comments ───────────────────────────────────────────────────────────────────

COMMENT ON TABLE  workflow_templates            IS 'Reusable, tenant-scoped workflow definitions (PRD §4 Workflow Template)';
COMMENT ON COLUMN workflow_templates.version    IS 'Incremented when a template is cloned (PRD FR-1/FR-2)';
COMMENT ON COLUMN workflow_templates.is_default IS 'Whether new students are auto-assigned this template; mirrors tenant_settings.default_workflow_template_id';
COMMENT ON COLUMN workflow_templates.is_archived IS 'Archived templates block new assignments but existing instances keep running (PRD FR-9)';

COMMENT ON TABLE  workflow_stages               IS 'Ordered steps within a workflow template (PRD §7 Stage Configuration)';
COMMENT ON COLUMN workflow_stages.stage_type    IS 'NORMAL | DOCUMENT_STAGE | APPLICATION_STAGE | VISA_STAGE | DECISION_STAGE | FINAL_STAGE';
COMMENT ON COLUMN workflow_stages.owner_role    IS 'Role responsible for this stage, e.g. ROLE_DOC_OFFICER (PRD §11)';
COMMENT ON COLUMN workflow_stages.sla_days      IS 'Service-level target in days for this stage; NULL = no SLA (PRD §12)';

COMMENT ON TABLE  workflow_transitions          IS 'Allowed directed moves between stages (PRD §9 Transition Management)';
COMMENT ON COLUMN workflow_transitions.transition_type IS 'FORWARD | BACKWARD | CONDITIONAL | MANUAL_APPROVAL';
