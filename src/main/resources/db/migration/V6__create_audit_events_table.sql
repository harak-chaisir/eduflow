-- ─────────────────────────────────────────────────────────────────────────────
-- V6: Create audit_events table (append-only audit trail)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE audit_events (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id    UUID,                              -- NULL for super-admin actions
    user_id      UUID,                              -- NULL for system-generated events
    action       VARCHAR(100) NOT NULL,             -- e.g. STUDENT_CREATED
    entity_type  VARCHAR(100) NOT NULL,             -- e.g. STUDENT, DOCUMENT
    entity_id    UUID         NOT NULL,             -- PK of the affected record
    old_value    TEXT,                              -- JSON or plain string representation
    new_value    TEXT,                              -- JSON or plain string representation

    -- Audit columns (no updated_at / updated_by — append-only)
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   VARCHAR(255),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by   VARCHAR(255),

    CONSTRAINT pk_audit_events PRIMARY KEY (id)
);

-- Indexes for common query patterns
CREATE INDEX idx_audit_events_tenant_id   ON audit_events (tenant_id);
CREATE INDEX idx_audit_events_entity      ON audit_events (entity_type, entity_id);
CREATE INDEX idx_audit_events_user_id     ON audit_events (user_id);
CREATE INDEX idx_audit_events_created_at  ON audit_events (created_at DESC);

-- Comments
COMMENT ON TABLE  audit_events             IS 'Immutable audit trail — append-only, never updated or deleted';
COMMENT ON COLUMN audit_events.tenant_id   IS 'Scoped tenant; NULL for platform super-admin actions';
COMMENT ON COLUMN audit_events.user_id     IS 'Staff user who triggered the event; NULL for system events';
COMMENT ON COLUMN audit_events.action      IS 'Business action, e.g. STUDENT_CREATED, DOCUMENT_APPROVED';
COMMENT ON COLUMN audit_events.entity_type IS 'Domain entity affected, e.g. STUDENT, DOCUMENT, APPLICATION';
COMMENT ON COLUMN audit_events.entity_id   IS 'Primary key UUID of the affected entity record';
COMMENT ON COLUMN audit_events.old_value   IS 'Previous state — plain string or JSON; NULL for create events';
COMMENT ON COLUMN audit_events.new_value   IS 'New state — plain string or JSON; NULL for delete events';

