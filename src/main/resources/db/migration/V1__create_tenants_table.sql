CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE tenants (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    name          VARCHAR(255) NOT NULL,
    slug          VARCHAR(100) NOT NULL,
    status        VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    VARCHAR(255),
    updated_by    VARCHAR(255),

    CONSTRAINT pk_tenants PRIMARY KEY (id),
    CONSTRAINT uq_tenants_slug UNIQUE (slug),
    CONSTRAINT chk_tenants_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED'))
);

CREATE INDEX idx_tenants_slug   ON tenants (slug);
CREATE INDEX idx_tenants_status ON tenants (status);

COMMENT ON TABLE  tenants            IS 'Top-level tenant (organisation) that owns all other resources';
COMMENT ON COLUMN tenants.slug       IS 'URL-safe unique identifier for the tenant';
COMMENT ON COLUMN tenants.status     IS 'Lifecycle state of the tenant: ACTIVE | INACTIVE | SUSPENDED';

