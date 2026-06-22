-- ─────────────────────────────────────────────────────────────────────────────
-- V19: Create courses table (belongs to a university; tenant-scoped)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE courses (
    id                  UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID          NOT NULL,
    university_id       UUID          NOT NULL,

    name                VARCHAR(255)  NOT NULL,
    level               VARCHAR(50)   NOT NULL,
    intake_month        INTEGER,
    intake_year         INTEGER,
    tuition_fee         NUMERIC(12,2),
    entry_requirements  VARCHAR(1000),

    active              BOOLEAN       NOT NULL DEFAULT TRUE,

    -- Audit columns
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),

    CONSTRAINT pk_courses              PRIMARY KEY (id),
    CONSTRAINT fk_courses_tenant       FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT fk_courses_university   FOREIGN KEY (university_id)
        REFERENCES universities (id) ON DELETE CASCADE,
    CONSTRAINT chk_courses_level       CHECK (level IN (
        'FOUNDATION', 'BACHELOR', 'MASTER', 'PHD', 'DIPLOMA', 'CERTIFICATE'
    )),
    CONSTRAINT chk_courses_intake_month CHECK (
        intake_month IS NULL OR (intake_month BETWEEN 1 AND 12)
    )
);

CREATE INDEX idx_courses_tenant_id     ON courses (tenant_id);
CREATE INDEX idx_courses_university_id ON courses (university_id);

COMMENT ON TABLE  courses               IS 'Courses offered by a university; tenant-scoped master data';
COMMENT ON COLUMN courses.tenant_id     IS 'Denormalized tenant for direct tenant-scoped queries';
COMMENT ON COLUMN courses.level         IS 'FOUNDATION | BACHELOR | MASTER | PHD | DIPLOMA | CERTIFICATE';
