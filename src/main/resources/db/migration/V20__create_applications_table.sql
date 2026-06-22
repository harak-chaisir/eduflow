-- ─────────────────────────────────────────────────────────────────────────────
-- V20: Create applications table (links a student to a course)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE applications (
    id                  UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID          NOT NULL,
    student_id          UUID          NOT NULL,
    course_id           UUID          NOT NULL,

    status              VARCHAR(50)   NOT NULL DEFAULT 'DRAFT',
    applied_date        TIMESTAMPTZ,
    decision_date       TIMESTAMPTZ,
    notes               VARCHAR(1000),

    -- Audit columns
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),

    CONSTRAINT pk_applications              PRIMARY KEY (id),
    CONSTRAINT fk_applications_tenant       FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT fk_applications_student      FOREIGN KEY (student_id)
        REFERENCES students (id) ON DELETE CASCADE,
    CONSTRAINT fk_applications_course       FOREIGN KEY (course_id)
        REFERENCES courses (id) ON DELETE CASCADE,
    CONSTRAINT uq_applications_student_course UNIQUE (student_id, course_id),
    CONSTRAINT chk_applications_status      CHECK (status IN (
        'DRAFT', 'SUBMITTED', 'UNDER_REVIEW',
        'CONDITIONAL_OFFER', 'UNCONDITIONAL_OFFER', 'REJECTED'
    ))
);

CREATE INDEX idx_applications_tenant_id  ON applications (tenant_id);
CREATE INDEX idx_applications_student_id ON applications (student_id);
CREATE INDEX idx_applications_course_id  ON applications (course_id);
CREATE INDEX idx_applications_status     ON applications (status);

COMMENT ON TABLE  applications          IS 'A student application to a university course; tenant-scoped';
COMMENT ON COLUMN applications.status   IS 'DRAFT | SUBMITTED | UNDER_REVIEW | CONDITIONAL_OFFER | UNCONDITIONAL_OFFER | REJECTED';
COMMENT ON COLUMN applications.applied_date  IS 'Set when transitioning to SUBMITTED';
COMMENT ON COLUMN applications.decision_date IS 'Set on offer / rejection';
