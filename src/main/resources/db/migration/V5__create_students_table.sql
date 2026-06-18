-- ─────────────────────────────────────────────────────────────────────────────
-- V5: Create students table and related element-collection tables
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE students (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,

    -- Personal information
    first_name          VARCHAR(100) NOT NULL,
    last_name           VARCHAR(100) NOT NULL,
    email               VARCHAR(255) NOT NULL,
    phone               VARCHAR(30),
    date_of_birth       TIMESTAMPTZ,
    gender              VARCHAR(30),
    nationality         VARCHAR(100),

    -- Address
    address_line1       VARCHAR(255),
    address_line2       VARCHAR(255),
    city                VARCHAR(100),
    state_province      VARCHAR(100),
    country             VARCHAR(100),
    postal_code         VARCHAR(20),

    -- Lifecycle
    status              VARCHAR(50)  NOT NULL DEFAULT 'LEAD',

    -- Assigned staff
    assigned_counselor_id UUID,

    -- Audit columns
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),

    CONSTRAINT pk_students                   PRIMARY KEY (id),
    CONSTRAINT uq_students_email_tenant      UNIQUE (email, tenant_id),
    CONSTRAINT fk_students_tenant            FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT fk_students_counselor         FOREIGN KEY (assigned_counselor_id)
        REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_students_status           CHECK (status IN (
        'LEAD', 'QUALIFIED', 'ACTIVE', 'ENROLLED', 'INACTIVE'
    )),
    CONSTRAINT chk_students_gender           CHECK (gender IN (
        'MALE', 'FEMALE', 'OTHER', 'PREFER_NOT_TO_SAY'
    ) OR gender IS NULL)
);

CREATE INDEX idx_students_tenant_id           ON students (tenant_id);
CREATE INDEX idx_students_email               ON students (email);
CREATE INDEX idx_students_status              ON students (status);
CREATE INDEX idx_students_assigned_counselor  ON students (assigned_counselor_id);
CREATE INDEX idx_students_last_name           ON students (last_name);

-- ─────────────────────────────────────────────────────────────────────────────
-- Element-collection: countries the student is interested in studying
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE student_interested_countries (
    student_id UUID         NOT NULL,
    country    VARCHAR(100) NOT NULL,

    CONSTRAINT pk_student_interested_countries
        PRIMARY KEY (student_id, country),
    CONSTRAINT fk_student_interested_countries_student
        FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE
);

CREATE INDEX idx_sic_student_id ON student_interested_countries (student_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- Element-collection: courses the student is interested in
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE student_interested_courses (
    student_id UUID         NOT NULL,
    course     VARCHAR(255) NOT NULL,

    CONSTRAINT pk_student_interested_courses
        PRIMARY KEY (student_id, course),
    CONSTRAINT fk_student_interested_courses_student
        FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE
);

CREATE INDEX idx_sico_student_id ON student_interested_courses (student_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- Comments
-- ─────────────────────────────────────────────────────────────────────────────

COMMENT ON TABLE  students                          IS 'Prospective and enrolled students; scoped to a tenant';
COMMENT ON COLUMN students.tenant_id                IS 'The consultancy this student belongs to';
COMMENT ON COLUMN students.status                   IS 'LEAD | QUALIFIED | ACTIVE | ENROLLED | INACTIVE';
COMMENT ON COLUMN students.gender                   IS 'MALE | FEMALE | OTHER | PREFER_NOT_TO_SAY';
COMMENT ON COLUMN students.assigned_counselor_id    IS 'Staff user responsible for this student';
COMMENT ON COLUMN students.date_of_birth            IS 'Stored as TIMESTAMPTZ; treat as date-only (midnight UTC)';

COMMENT ON TABLE  student_interested_countries      IS 'Countries the student is interested in studying in';
COMMENT ON TABLE  student_interested_courses        IS 'Courses / fields the student is interested in';

