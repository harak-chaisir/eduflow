-- ─────────────────────────────────────────────────────────────────────────────
-- V18: Create universities table (tenant-scoped master data)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE universities (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,

    name                VARCHAR(255) NOT NULL,
    country             VARCHAR(100) NOT NULL,
    city                VARCHAR(100),
    website             VARCHAR(255),
    code                VARCHAR(50),

    active              BOOLEAN      NOT NULL DEFAULT TRUE,

    -- Audit columns
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),

    CONSTRAINT pk_universities             PRIMARY KEY (id),
    CONSTRAINT fk_universities_tenant      FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT uq_universities_name_tenant UNIQUE (name, country, tenant_id)
);

CREATE INDEX idx_universities_tenant_id ON universities (tenant_id);
CREATE INDEX idx_universities_country   ON universities (country);

COMMENT ON TABLE  universities           IS 'Universities curated by a tenant; master data for applications';
COMMENT ON COLUMN universities.tenant_id IS 'The consultancy this university belongs to';
COMMENT ON COLUMN universities.active    IS 'Soft enable/disable instead of hard delete';
